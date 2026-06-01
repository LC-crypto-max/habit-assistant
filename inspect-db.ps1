param(
    [string]$DatabaseUrl = "jdbc:h2:file:./data/assistantdb;AUTO_SERVER=TRUE",
    [string]$User = "sa",
    [string]$Password = "",
    [switch]$NoFileLock
)

$ErrorActionPreference = "Stop"

chcp 65001 | Out-Null
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if ($NoFileLock) {
    $DatabaseUrl = "jdbc:h2:file:./data/assistantdb;FILE_LOCK=NO"
}

$h2Jar = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\h2database\h2" -Recurse -Filter "h2-*.jar" |
    Sort-Object LastWriteTime |
    Select-Object -Last 1 -ExpandProperty FullName

if (-not $h2Jar) {
    throw "Cannot find H2 jar under $env:USERPROFILE\.m2\repository\com\h2database\h2. Run mvn test first."
}

$workDir = Join-Path (Get-Location) "target\db-inspector"
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

$javaFile = Join-Path $workDir "DbInspector.java"
$classFile = Join-Path $workDir "DbInspector.class"

$javaSource = @'
import java.sql.*;

public class DbInspector {
    private static final String[] QUERIES = new String[] {
        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME",
        "SELECT COUNT(*) AS USER_ACTIVITY_COUNT FROM USER_ACTIVITY",
        "SELECT COUNT(*) AS INTEREST_TERM_COUNT FROM INTEREST_TERM",
        "SELECT COUNT(*) AS CONTENT_ITEM_COUNT FROM CONTENT_ITEM",
        "SELECT COUNT(*) AS RECOMMENDATION_COUNT FROM RECOMMENDATION",
        "SELECT ID, TYPE, PLATFORM, TITLE, TEXT FROM USER_ACTIVITY ORDER BY ID DESC LIMIT 20",
        "SELECT ID, USER_ID, TERM, WEIGHT, HIT_COUNT FROM INTEREST_TERM ORDER BY WEIGHT DESC, ID LIMIT 30",
        "SELECT R.ID, R.RECOMMENDATION_DATE, R.SCORE, R.FEEDBACK, C.PLATFORM, C.TITLE, R.REASON FROM RECOMMENDATION R LEFT JOIN CONTENT_ITEM C ON R.CONTENT_ITEM_ID = C.ID ORDER BY R.ID DESC LIMIT 20"
    };

    public static void main(String[] args) throws Exception {
        String url = args[0];
        String user = args[1];
        String password = args.length >= 3 ? args[2] : "";

        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            for (String sql : QUERIES) {
                System.out.println();
                System.out.println("SQL> " + sql);
                try (ResultSet rs = statement.executeQuery(sql)) {
                    printResultSet(rs);
                }
            }
        }
    }

    private static void printResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        int[] widths = new int[columns];
        String[] names = new String[columns];

        for (int i = 1; i <= columns; i++) {
            names[i - 1] = meta.getColumnLabel(i);
            widths[i - 1] = Math.max(names[i - 1].length(), 12);
        }

        java.util.List<String[]> rows = new java.util.ArrayList<>();
        while (rs.next()) {
            String[] row = new String[columns];
            for (int i = 1; i <= columns; i++) {
                String value = rs.getString(i);
                row[i - 1] = value == null ? "" : value;
                widths[i - 1] = Math.min(Math.max(widths[i - 1], row[i - 1].length()), 60);
            }
            rows.add(row);
        }

        printRow(names, widths);
        for (int i = 0; i < columns; i++) {
            System.out.print("-".repeat(widths[i]));
            if (i < columns - 1) {
                System.out.print(" | ");
            }
        }
        System.out.println();

        for (String[] row : rows) {
            printRow(row, widths);
        }
        System.out.println("(" + rows.size() + " rows)");
    }

    private static void printRow(String[] values, int[] widths) {
        for (int i = 0; i < values.length; i++) {
            String value = values[i] == null ? "" : values[i];
            if (value.length() > widths[i]) {
                value = value.substring(0, Math.max(0, widths[i] - 3)) + "...";
            }
            System.out.print(padRight(value, widths[i]));
            if (i < values.length - 1) {
                System.out.print(" | ");
            }
        }
        System.out.println();
    }

    private static String padRight(String value, int width) {
        int padding = width - value.length();
        if (padding <= 0) {
            return value;
        }
        return value + " ".repeat(padding);
    }
}
'@

[System.IO.File]::WriteAllText($javaFile, $javaSource, [System.Text.UTF8Encoding]::new($false))

Write-Host "Using H2 jar: $h2Jar"
Write-Host "Database URL: $DatabaseUrl"

javac -encoding UTF-8 -cp $h2Jar -d $workDir $javaFile
java '-Dfile.encoding=UTF-8' -cp "$workDir;$h2Jar" DbInspector $DatabaseUrl $User $Password

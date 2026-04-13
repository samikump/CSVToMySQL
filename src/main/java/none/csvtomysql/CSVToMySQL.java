package none.csvtomysql;

/**
 * CSV to MySQL Converter
 * 
 * @author samikump
 * 
 */

import com.opencsv.*;
import com.opencsv.exceptions.CsvValidationException;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class CSVToMySQL extends Application {

    private Stage primaryStage;
    private TextField csvPathField;
    private TextField dbNameField;
    private TextField tableNameField;
    private TextField scriptNameField;
    private char decimalSeparator = '.';

    public static void main(String[] args) {
        launch(args);
    } // main method ends here

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("CSV to MySQL Converter");
        showMainMenu();
    } // start method ends here

    private void showMainMenu() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        csvPathField = new TextField();
        csvPathField.setEditable(false);
        csvPathField.setPrefWidth(300);
        Button browseBtn = new Button("Browse CSV");
        browseBtn.setOnAction(e -> selectCSVFile());

        dbNameField = new TextField();
        tableNameField = new TextField();
        scriptNameField = new TextField();

        grid.add(new Label("CSV File:"), 0, 0);
        grid.add(csvPathField, 1, 0);
        grid.add(browseBtn, 2, 0);

        grid.add(new Label("Database Name:"), 0, 1);
        grid.add(dbNameField, 1, 1);

        grid.add(new Label("Table Name:"), 0, 2);
        grid.add(tableNameField, 1, 2);

        grid.add(new Label("SQL Script Name:"), 0, 3);
        grid.add(scriptNameField, 1, 3);

        Button convertBtn = new Button("Convert to MySQL");
        convertBtn.setStyle("-fx-font-weight: bold; -fx-base: #4CAF50;");
        convertBtn.setOnAction(e -> processConversion());

        root.getChildren().addAll(new Label("Configure Conversion Settings:"), grid, convertBtn);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();
    } // showMainMenu method ends here

    private void selectCSVFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            csvPathField.setText(selectedFile.getAbsolutePath());
            // Auto-fill table name and script name from file name
            String fileName = selectedFile.getName();
            if (fileName.contains(".")) {
                String baseName = fileName.substring(0, fileName.lastIndexOf("."));
                tableNameField.setText(baseName.replaceAll("[^a-zA-Z0-9_]", "_"));
                scriptNameField.setText(baseName + ".sql");
            } else {
                tableNameField.setText(fileName.replaceAll("[^a-zA-Z0-9_]", "_"));
                scriptNameField.setText(fileName + ".sql");
            }
        }
    } // selectCSVFile method ends here

    private void processConversion() {
        String csvPath = csvPathField.getText();
        String dbName = dbNameField.getText().trim();
        String tableName = tableNameField.getText().trim();
        String scriptName = scriptNameField.getText().trim();

        if (csvPath.isEmpty() || dbName.isEmpty() || tableName.isEmpty() || scriptName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "All fields are required.");
            return;
        }

        File csvFile = new File(csvPath);
        File scriptFile = new File(csvFile.getParent(), scriptName);

        if (scriptFile.exists()) {
        boolean overwrite = showConfirm("Overwrite Warning", "File already exists", 
                                   "The file " + scriptName + " already exists. Overwrite it?");
            if (!overwrite) {
                return;
            }
        }
        
        try {
            generateSQLScript(csvFile, scriptFile, dbName, tableName);
            showAlert(Alert.AlertType.INFORMATION, "Success", "SQL script generated successfully at:\n" + scriptFile.getAbsolutePath());
            boolean convertAnother = showConfirm("Continue?", "Conversion Complete", "Do you want to convert another CSV file?");
            if (convertAnother) {
                resetForm();
            } else {
                primaryStage.close();
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Conversion Failed", "An error occurred: " + e.getMessage());
            e.printStackTrace();
        } // try-catch ends here
    } // processConversion method ends here

    private void resetForm() {
        csvPathField.clear();
        dbNameField.clear();
        tableNameField.clear();
        scriptNameField.clear();
    } // resetForm method ends here

    private char detectSeparator(File csvFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile, StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine == null) return ',';
            int commas = 0;
            int semicolons = 0;
            for (char c : firstLine.toCharArray()) {
                if (c == ',') commas++;
                else if (c == ';') semicolons++;
            }
            return (semicolons > commas) ? ';' : ',';
        }
    } // detectSeparator method ends here

    private void generateSQLScript(File csvFile, File scriptFile, String dbName, String tableName) throws IOException, CsvValidationException {
        List<String[]> data = new ArrayList<>();
        String[] headers;

        char separator = detectSeparator(csvFile);
        CSVParser parser = new CSVParserBuilder().withSeparator(separator).build();

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(csvFile, StandardCharsets.UTF_8))
                .withCSVParser(parser)
                .build()) {
            headers = reader.readNext();
            if (headers == null) throw new IOException("CSV file is empty.");
            
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                data.add(nextLine);
            }
        }

        detectDecimalSeparator(data);
        String[] types = detectTypes(headers, data);

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile, StandardCharsets.UTF_8)))) {
            writer.println("-- Generated by CSVToMySQL Converter");
            writer.println("SET GLOBAL max_allowed_packet = 1073741824;");
            writer.println("SET NAMES utf8mb4;");
            writer.println("CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
            writer.println("USE `" + dbName + "`;");
            writer.println();
            writer.println("DROP TABLE IF EXISTS `" + tableName + "`;");
            writer.println();
            
            StringBuilder createTable = new StringBuilder("CREATE TABLE `" + tableName + "` (\n");
            for (int i = 0; i < headers.length; i++) {
                createTable.append("  `").append(headers[i].trim()).append("` ").append(types[i]);
                if (i < headers.length - 1) createTable.append(",");
                createTable.append("\n");
            }
            createTable.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
            writer.println(createTable.toString());
            writer.println();

            writer.println("-- Data Insertion");
            writer.println("DELIMITER //");
            writer.println("DROP PROCEDURE IF EXISTS do_insert //");
            writer.println("CREATE PROCEDURE do_insert()");
            writer.println("BEGIN");
            writer.println("    DECLARE EXIT HANDLER FOR SQLEXCEPTION");
            writer.println("    BEGIN");
            writer.println("        ROLLBACK;"); // Rollback on any error
            writer.println("    END;");
            writer.println("    START TRANSACTION;");
            for (String[] row : data) {
                StringBuilder insert = new StringBuilder("INSERT INTO `" + tableName + "` VALUES (");
                for (int i = 0; i < headers.length; i++) {
                    String val = (i < row.length) ? row[i] : null;
                    if (val == null || val.isEmpty()) {
                        insert.append("NULL");
                    } else if (types[i].startsWith("VARCHAR") || types[i].equals("DATETIME")) {
                        String valueToInsert = val;
                        if (types[i].equals("DATETIME")) {
                            valueToInsert = formatToMySQLDateTime(val);
                        }
                        insert.append("'").append(escapeSql(valueToInsert)).append("'");
                    } else if (types[i].startsWith("NUMERIC")) {
                        String normalizedVal = val;
                        if (decimalSeparator == ',') {
                            normalizedVal = val.replace(',', '.');
                        }
                        insert.append(normalizedVal);
                    } else {
                        insert.append(val);
                    }
                    if (i < headers.length - 1) insert.append(", ");
                }
                insert.append(");");
                writer.println(insert.toString());
            }
            writer.println("    COMMIT;");
            writer.println("END //");
            writer.println("DELIMITER ;");
            writer.println("CALL do_insert();");
            writer.println("DROP PROCEDURE do_insert;");
        }
    } // generateSQLScript method ends here

    private void detectDecimalSeparator(List<String[]> data) {
        int dotCount = 0;
        int commaCount = 0;
        for (String[] row : data) {
            for (String val : row) {
                if (val == null || val.isEmpty()) continue;
                if (val.matches("^-?\\d+\\.\\d+$")) dotCount++;
                else if (val.matches("^-?\\d+,\\d+$")) commaCount++;
            }
        }
        decimalSeparator = (commaCount > dotCount) ? ',' : '.';
    } // detectDecimalSeparator method ends here

    private String[] detectTypes(String[] headers, List<String[]> data) {
        String[] types = new String[headers.length];
        for (int col = 0; col < headers.length; col++) {
            boolean isInt = true;
            boolean fitsInInt = true;
            boolean isNumeric = true;
            boolean isDateTime = true;
            int maxLen = 0;

            for (String[] row : data) {
                if (col >= row.length) continue;
                String val = row[col];
                if (val == null || val.isEmpty()) continue;

                maxLen = Math.max(maxLen, val.length());

                if (isInt) {
                    try {
                        long l = Long.parseLong(val);
                        if (fitsInInt && (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE)) {
                            fitsInInt = false;
                        }
                    } catch (NumberFormatException e) {
                        isInt = false;
                        fitsInInt = false;
                    }
                }

                if (isNumeric) {
                    String normalizedVal = val;
                    if (decimalSeparator == ',') {
                        normalizedVal = val.replace(',', '.');
                    }
                    try {
                        Double.parseDouble(normalizedVal);
                    } catch (NumberFormatException e) {
                        isNumeric = false;
                    }
                }

                if (isDateTime) {
                    if (!isValidDateTime(val)) {
                        isDateTime = false;
                    }
                }
            }

            if (isInt) {
                types[col] = fitsInInt ? "INT" : "BIGINT";
            } else if (isNumeric) {
                types[col] = "NUMERIC(19,4)";
            } else if (isDateTime) {
                types[col] = "DATETIME";
            } else {
                int varcharLen = ((maxLen + 19) / 20 * 20) + 20;
                types[col] = "VARCHAR(" + varcharLen + ")";
            }
        }
        return types;
    } // detectTypes method ends here

    private String formatToMySQLDateTime(String val) {
        String[] patterns = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"
          , "MM/dd/yyyy HH:mm:ss", "MM/dd/yyyy"
          , "M/dd/yyyy HH:mm:ss", "M/dd/yyyy"
          , "MM/d/yyyy HH:mm:ss", "MM/d/yyyy"
          , "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy"
          , "d.M.yyyy HH:mm:ss", "d.M.yyyy"
          , "dd.M.yyyy HH:mm:ss", "dd.M.yyyy"
          , "d.MM.yyyy HH:mm:ss", "d.MM.yyyy"
        };
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                if (pattern.contains("HH:mm:ss")) {
                    return LocalDateTime.parse(val, formatter).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } else {
                    return LocalDate.parse(val, formatter).atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
            } catch (DateTimeParseException ignored) {}
        }
        return val;
    } // formatToMySQLDateTime method ends here

    private boolean isValidDateTime(String val) {
        String[] patterns = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"
          , "MM/dd/yyyy HH:mm:ss", "MM/dd/yyyy"
          , "M/dd/yyyy HH:mm:ss", "M/dd/yyyy"
          , "MM/d/yyyy HH:mm:ss", "MM/d/yyyy"
          , "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy"
          , "d.M.yyyy HH:mm:ss", "d.M.yyyy"
          , "dd.M.yyyy HH:mm:ss", "dd.M.yyyy"
          , "d.MM.yyyy HH:mm:ss", "d.MM.yyyy"
        };
        for (String pattern : patterns) {
            try {
                DateTimeFormatter.ofPattern(pattern).parse(val);
                return true;
            } catch (DateTimeParseException ignored) {}
        }
        return false;
    } // isValidDateTime method ends here

    private String escapeSql(String str) {
        if (str == null) return null;
        return str.replace("\\", "\\\\")
                  .replace("'", "''")
                  .replace("\"", "\\\"");
    } // escapeSql method ends here

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    } // showAlert method ends here
    
    private boolean showConfirm(String title, String header, String content) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(title);
    alert.setHeaderText(header);
    alert.setContentText(content);

    ButtonType yesButton = new ButtonType("Yes", ButtonBar.ButtonData.YES);
    ButtonType noButton = new ButtonType("No", ButtonBar.ButtonData.NO);
    alert.getButtonTypes().setAll(yesButton, noButton);

    return alert.showAndWait().filter(res -> res == yesButton).isPresent();
    } // showConfirm method ends here
} // class CSVToMySQL ends here
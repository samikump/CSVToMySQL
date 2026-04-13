## CSV to MySQL Data Migration Tool

A Java application that converts CSV files into runnable MySQL scripts.

### Why this project?

Nowadays, many companies and organizations share their data in CSV file format.
<br />
The receiver often needs to insert this data into their database.
<br />
This application makes runnable MySQL scripts for data insertion, including database creation (if doesn't exist already) and table creation.

### 🚀 Core Functionalities

* **Smart Data Detection:** Automatically identifies decimal separators (comma/period) and maps CSV columns to SQL data types.
* **Encoding & Character Safety:** Ensures special characters (like € and $) are preserved via UTF-8 encoding and handles SQL injection risks by escaping single quotes.
* **High-Performance Migration:** Uses SQL Transaction (COMMIT/ROLLBACK) to disable auto-commit, allowing 1M+ rows to be processed in minutes.
* **User-Friendly Workflow:** Includes file overwrite warning and the option to process multiple files in one session.

### Testing

I tested the application using two datasets from *avoindata.fi*. Both of them were converted smoothly.
<br />
Even the one with more than a million rows was converted in a few minutes and running the resulting SQL script also only takes a few minutes.

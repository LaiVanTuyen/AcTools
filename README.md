# AcTools

## Overview

AcTools is a Java-based automation tool designed to automate interactions with web pages using Selenium WebDriver. The tool is built with JavaFX for the user interface and Maven for dependency management.

## Features

- Automates web interactions using Selenium WebDriver.
- Supports importing URLs from a text file.
- Provides a user interface to input credentials and URLs.
- Displays progress and status updates during automation.
- Handles various web elements and interactions, including login forms and buttons.
- Logs failed URLs for further analysis.

## Requirements

- Java 21 or higher
- Maven 3.6.0 or higher

## Dependencies

The project uses the following dependencies:

- JavaFX (controls and FXML)
- Selenium WebDriver (ChromeDriver)
- WebDriverManager
- JUnit 5 (for testing)
- Guava (for utility functions)

## Setup

1. **Clone the repository:**

   ```sh
   git clone https://github.com/yourusername/AcTools.git
   cd AcTools
   
## Build jar, exe
1. **Build jar:**
   ```sh
   java --module-path "C:\SetUp\Java\javafx-sdk-21.0.6/lib" --add-modules javafx.controls,javafx.fxml -jar target/AcTools-1.0-SNAPSHOT.jar
2. **Build exe:**
   ```sh
   jpackage --name AcTools --input target/ --main-jar AcTools-1.0-SNAPSHOT.jar --main-class com.vti.tuyn.actools.AcToolsApplication --type exe --module-path       "C:\SetUp\Java\javafx-sdk-21.0.6/lib" --add-modules javafx.controls,javafx.fxml --win-console

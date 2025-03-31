package com.vti.tuyn.actools;
import io.github.bonigarcia.wdm.WebDriverManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.interactions.Actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.scene.control.Label;

public class AcToolsController {
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextArea urlInput;

    @FXML
    private Button importButton;

    @FXML
    private Button startButton;

    @FXML
    private Button clearButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label statusLabel;

    private final List<String> urls = new ArrayList<>();
    private final List<String> failedUrls = new ArrayList<>();
    private int successCount = 0;
    private int failureCount = 0;
    private int noPointsCount = 0;
    private int topPageCount = 0;
    private int giftTransferredCount = 0;
    private int errorMessageCount = 0;
    private ExecutorService executorService;
    private static final int MAX_CONCURRENT_THREADS = 5;
    private List<WebDriver> activeDrivers = new ArrayList<>();
    private static final long MIN_DELAY = 2000;
    private static final long MAX_DELAY = 5000;

    @FXML
    public void initialize() {
        importButton.setOnAction(event -> importUrlsFromFile());
        startButton.setOnAction(event -> startAutomation());
        clearButton.setOnAction(event -> clearAll());
    }

    private void importUrlsFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a TXT File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = fileChooser.showOpenDialog(importButton.getScene().getWindow());
        if (file != null) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        urls.add(line);
                    }
                }
                updateStatus("URLs imported successfully from file!");
            } catch (IOException e) {
                updateStatus("Error reading file: " + e.getMessage());
            }
        }
    }

    private void startAutomation() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            updateStatus("Please enter both username and password.");
            return;
        }

        // Clear previous results
        closeAllBrowsers();
        urls.clear();
        failedUrls.clear();
        successCount = 0;
        failureCount = 0;
        noPointsCount = 0;
        topPageCount = 0;
        giftTransferredCount = 0;
        errorMessageCount = 0;

        // Get URLs from text area
        String manualUrls = urlInput.getText().trim();
        if (!manualUrls.isEmpty()) {
            String[] urlArray = manualUrls.split("\\r?\\n");
            for (String url : urlArray) {
                url = url.trim();
                if (!url.isEmpty()) {
                    urls.add(url);
                }
            }
        }

        if (urls.isEmpty()) {
            updateStatus("No URLs to process.");
            return;
        }

        // Disable buttons during automation
        setControlsEnabled(false);

        // Initialize progress
        progressBar.setProgress(0);
        updateStatus(String.format("Starting automation with %d URLs...", urls.size()));

        // Process URLs in smaller batches with longer delays
        new Thread(() -> {
            try {
                for (int i = 0; i < urls.size(); i += MAX_CONCURRENT_THREADS) {
                    // Calculate the size of current batch
                    int batchEnd = Math.min(i + MAX_CONCURRENT_THREADS, urls.size());
                    List<String> currentBatch = urls.subList(i, batchEnd);

                    // Create thread pool for current batch
                    executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_THREADS);

                    // Submit current batch for processing
                    for (String url : currentBatch) {
                        executorService.execute(() -> processUrl(url));
                    }

                    // Wait for current batch to complete
                    executorService.shutdown();
                    while (!executorService.isTerminated()) {
                        Thread.sleep(1000);
                        updateProgress();
                    }

                    // Close all browsers after batch is complete
                    closeAllBrowsers();

                    // Giảm delay giữa các batch để tăng tốc độ
                    int remainingUrls = urls.size() - (i + currentBatch.size());
                    if (remainingUrls > 0) {
                        long batchDelay = (long) (Math.random() * 2000) + 1000; // Giảm xuống 1-3 giây
                        Thread.sleep(batchDelay);
                    }
                }

                // All batches completed
                Platform.runLater(() -> {
                    setControlsEnabled(true);
                    if (!failedUrls.isEmpty()) {
                        writeFailedUrlsToFile();
                        updateStatus(String.format(
                                "Completed! Success: %d, Failed: %d, No Points: %d, Transferred: %d, Top Page: %d\nFailed URLs: %s",
                                successCount, failureCount, noPointsCount, giftTransferredCount, topPageCount,
                                String.join("\n", failedUrls)));
                    } else {
                        updateStatus(String.format(
                                "All completed! Total: %d, No Points: %d, Transferred: %d, Top Page: %d",
                                successCount, noPointsCount, giftTransferredCount, topPageCount));
                    }
                });
            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    setControlsEnabled(true);
                    updateStatus("Automation interrupted: " + e.getMessage());
                });
            }
        }).start();
    }

    private void setControlsEnabled(boolean enabled) {
        Platform.runLater(() -> {
            startButton.setDisable(!enabled);
            importButton.setDisable(!enabled);
            clearButton.setDisable(!enabled);
            usernameField.setDisable(!enabled);
            passwordField.setDisable(!enabled);
            urlInput.setDisable(!enabled);
        });
    }

    private void updateProgress() {
        int total = urls.size();
        double progress = (double) (successCount + failureCount) / total;
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            String status = String.format("Progress: %d/%d (Success: %d, Failed: %d)\n",
                    (successCount + failureCount), total, successCount, failureCount);
            status += String.format("Không có điểm: %d, Top Page: %d, Đã chuyển quà: %d, Error Messages: %d",
                    noPointsCount, topPageCount, giftTransferredCount, errorMessageCount);
            statusLabel.setText(status);
        });

        // Nếu đã xử lý hết tất cả các URL, đóng tất cả trình duyệt
        if (successCount + failureCount >= total) {
            Platform.runLater(() -> {
                String finalStatus = String.format(
                        "All completed! Total: %d\nKhông có điểm: %d, Top Page: %d, Đã chuyển quà: %d, Error Messages: %d",
                        total, noPointsCount, topPageCount, giftTransferredCount, errorMessageCount);
                statusLabel.setText(finalStatus);
            });

            // Đóng tất cả trình duyệt sau 5 giây
            try {
                Thread.sleep(5000);
                closeAllBrowsers();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void writeFailedUrlsToFile() {
        try (FileWriter writer = new FileWriter("failed_urls.txt")) {
            writer.write("Failed URLs:\n");
            writer.write("============\n");
            for (String url : failedUrls) {
                writer.write(url + "\n");
            }
            writer.write(String.format("\nTotal Failed: %d\n", failedUrls.size()));
            writer.write(String.format("Total Success: %d\n", successCount));
            writer.write(String.format("Total No Points: %d\n", noPointsCount));
            writer.write(String.format("Total Top Page: %d\n", topPageCount));
            writer.write(String.format("Total Gift Transferred: %d\n", giftTransferredCount));
            writer.write(String.format("Total Processed: %d\n", urls.size()));
        } catch (IOException e) {
            Platform.runLater(() -> statusLabel.setText("Error writing failed URLs: " + e.getMessage()));
        }
    }

    private synchronized void addDriver(WebDriver driver) {
        activeDrivers.add(driver);
    }

    private synchronized void removeDriver(WebDriver driver) {
        activeDrivers.remove(driver);
    }

    private void closeAllBrowsers() {
        synchronized (activeDrivers) {
            for (WebDriver driver : activeDrivers) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            activeDrivers.clear();
        }
    }

    private void clearAll() {
        if (executorService != null && !executorService.isTerminated()) {
            executorService.shutdownNow();
        }
        closeAllBrowsers();
        urls.clear();
        failedUrls.clear();
        successCount = 0;
        failureCount = 0;
        noPointsCount = 0;
        topPageCount = 0;
        giftTransferredCount = 0;
        errorMessageCount = 0;
        usernameField.clear();
        passwordField.clear();
        urlInput.clear();
        progressBar.setProgress(0);
        setControlsEnabled(true);
        updateStatus("All cleared.");
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText("Status: " + message));
    }

//   private void processUrl(String url) {
//        WebDriver driver = null;
//        try {
//            WebDriverManager.chromedriver().setup();
//            ChromeOptions options = new ChromeOptions();
//            options.addArguments("--disable-notifications");
//            options.addArguments("--start-maximized");
//            options.addArguments("--disable-gpu");
//            options.addArguments("--no-sandbox");
//            options.addArguments("--disable-dev-shm-usage");
//            options.addArguments("--disable-blink-features=AutomationControlled");
//            options.addArguments("--disable-extensions");
//            options.addArguments("--disable-popup-blocking");
//            options.addArguments("--disable-web-security");
//            options.addArguments("--allow-running-insecure-content");
//            options.addArguments("--disable-site-isolation-trials");
//            options.addArguments("--disable-features=IsolateOrigins,site-per-process");
//            options.addArguments("--disable-site-isolation-for-policy");
//            options.addArguments(
//                    "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
//
//            // Thêm preferences để tránh phát hiện automation
//            options.setExperimentalOption("excludeSwitches", new String[] { "enable-automation" });
//            options.setExperimentalOption("useAutomationExtension", false);
//
//            // Khởi tạo WebDriver
//            driver = new ChromeDriver(options);
//            addDriver(driver);
//            driver.manage().window().maximize();
//            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(120)); // Tăng thời gian chờ lên 120 giây
//
//            // Thêm delay ngẫu nhiên trước khi bắt đầu xử lý URL mới
//            try {
//                long randomDelay = (long) (Math.random() * 8000) + 5000; // Tăng lên 5-13 giây
//                Thread.sleep(randomDelay);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//
//            // Lấy username và password từ form chính
//            String username = usernameField.getText();
//            String password = passwordField.getText();
//
//            // Navigate to the URL
//            driver.get(url);
//            updateStatus("Đang mở trang: " + url);
//
//            // Đợi cho trang load hoàn tất
//            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
//                    .executeScript("return document.readyState").equals("complete"));
//
//            // Thêm delay ngẫu nhiên sau khi load trang
//            Thread.sleep((long) (Math.random() * 3000) + 2000); // Tăng lên 2-5 giây
//
//            // Đợi section chứa button xuất hiện với nhiều cách khác nhau
//            boolean sectionFound = false;
//            WebElement button = null;
//
//            // Cách 1: Tìm theo CSS selector
//            try {
//                WebElement section = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("section.css-14yxxc1")));
//                if (section.isDisplayed()) {
//                    Thread.sleep(2000); // Thêm delay 2 giây
//                    WebElement heroSection = section.findElement(By.cssSelector("div[data-testid='signInPageHeroSection']"));
//                    if (heroSection.isDisplayed()) {
//                        Thread.sleep(2000); // Thêm delay 2 giây
//                        button = heroSection.findElement(By.xpath(".//button[contains(text(), 'ギフトをポイントに移行する')]"));
//                        if (button.isDisplayed() && button.isEnabled()) {
//                            sectionFound = true;
//                        }
//                    }
//                }
//            } catch (Exception e) {
//                // Thử cách khác nếu cách 1 thất bại
//            }
//
//            // Cách 2: Tìm theo XPath
//            if (!sectionFound) {
//                try {
//                    Thread.sleep(3000); // Thêm delay 3 giây
//                    button = wait.until(ExpectedConditions.presenceOfElementLocated(
//                            By.xpath("//button[contains(text(), 'ギフトをポイントに移行する')]")));
//                    if (button.isDisplayed() && button.isEnabled()) {
//                        sectionFound = true;
//                    }
//                } catch (Exception e) {
//                    // Thử cách khác nếu cách 2 thất bại
//                }
//            }
//
//            // Cách 3: Tìm theo data-gtm-click
//            if (!sectionFound) {
//                try {
//                    Thread.sleep(3000); // Thêm delay 3 giây
//                    button = wait.until(ExpectedConditions.presenceOfElementLocated(
//                            By.xpath("//button[@data-gtm-click='shared-loginButton-loginButton' and contains(text(), 'ギフトをポイントに移行する')]")));
//                    if (button.isDisplayed() && button.isEnabled()) {
//                        sectionFound = true;
//                    }
//                } catch (Exception e) {
//                    // Thử cách khác nếu cách 3 thất bại
//                }
//            }
//
//            // Cách 4: Tìm theo JavaScript
//            if (!sectionFound) {
//                try {
//                    Thread.sleep(3000); // Thêm delay 3 giây
//                    button = (WebElement) ((JavascriptExecutor) driver).executeScript(
//                            "return document.querySelector('button:contains(\"ギフトをポイントに移行する\")')");
//                    if (button != null && button.isDisplayed() && button.isEnabled()) {
//                        sectionFound = true;
//                    }
//                } catch (Exception e) {
//                    // Thử cách khác nếu cách 4 thất bại
//                }
//            }
//
//            if (!sectionFound || button == null) {
//                throw new Exception("Không tìm thấy section chứa button");
//            }
//
//            // Thêm delay ngẫu nhiên trước khi click
//            Thread.sleep((long) (Math.random() * 2000) + 2000); // Tăng lên 2-4 giây
//
//            // Thử click button với nhiều cách khác nhau
//            boolean buttonClicked = false;
//
//            // Cách 1: Click thông thường
//            try {
//                button.click();
//                buttonClicked = true;
//            } catch (Exception e) {
//                // Thử cách khác nếu cách 1 thất bại
//            }
//
//            // Cách 2: Click bằng JavaScript
//            if (!buttonClicked) {
//                try {
//                    Thread.sleep(2000); // Thêm delay 2 giây
//                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);
//                    buttonClicked = true;
//                } catch (Exception e) {
//                    // Thử cách khác nếu cách 2 thất bại
//                }
//            }
//
//            // Cách 3: Click bằng Actions
//            if (!buttonClicked) {
//                try {
//                    Thread.sleep(2000); // Thêm delay 2 giây
//                    new Actions(driver).moveToElement(button).click().perform();
//                    buttonClicked = true;
//                } catch (Exception e) {
//                    // Thử cách khác nếu cách 3 thất bại
//                }
//            }
//
//            // Cách 4: Click bằng JavaScript với scroll
//            if (!buttonClicked) {
//                try {
//                    Thread.sleep(2000); // Thêm delay 2 giây
//                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", button);
//                    Thread.sleep(2000); // Đợi 2 giây sau khi scroll
//                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);
//                    buttonClicked = true;
//                } catch (Exception e) {
//                    // Thử cách khác nếu cách 4 thất bại
//                }
//            }
//
//            if (!buttonClicked) {
//                throw new Exception("Không thể click button đăng nhập");
//            }
//
//            // Đợi form đăng nhập Line load
//            updateStatus("Đang đợi form đăng nhập Line...");
//            Thread.sleep(3000); // Tăng lên 3 giây
//
//            // Kiểm tra xem đã chuyển sang trang đăng nhập chưa
//            boolean loginFormLoaded = false;
//            for (int attempt = 0; attempt < 10 && !loginFormLoaded; attempt++) { // Tăng số lần thử lên 10
//                try {
//                    WebElement emailInput = driver.findElement(By.cssSelector("input[name='tid']"));
//                    WebElement passwordInput = driver.findElement(By.cssSelector("input[name='tpasswd']"));
//                    if (emailInput.isDisplayed() && passwordInput.isDisplayed()) {
//                        loginFormLoaded = true;
//                    }
//                } catch (Exception e) {
//                    Thread.sleep(2000); // Tăng lên 2 giây
//                }
//            }
//
//            if (!loginFormLoaded) {
//                throw new Exception("Không thể load form đăng nhập Line");
//            }
//
//            // Tìm các trường input
//            WebElement emailInputField = driver.findElement(By.cssSelector("input[name='tid']"));
//            WebElement passwordInputField = driver.findElement(By.cssSelector("input[name='tpasswd']"));
//
//            // Điền thông tin đăng nhập với delay
//            emailInputField.sendKeys(username);
//            Thread.sleep(2000); // Tăng lên 2 giây
//            passwordInputField.sendKeys(password);
//            Thread.sleep(2000); // Tăng lên 2 giây
//
//            // Đợi nút submit và click
//            updateStatus("Đang đợi nút đăng nhập được kích hoạt...");
//            WebElement submitButton = driver.findElement(By.cssSelector("button.MdBtn01[type='submit']"));
//            Thread.sleep(2000); // Tăng lên 2 giây
//            submitButton.click();
//
//            // Đợi sau khi submit
//            Thread.sleep(5000); // Tăng lên 5 giây
//
//            // Thêm delay ngẫu nhiên giữa các lần kiểm tra
//            Thread.sleep((long) (Math.random() * 3000) + 2000); // Tăng lên 2-5 giây
//
//            // Kiểm tra text lỗi hệ thống và đếm số link
//            boolean hasSystemError = false;
//            int totalTopPageLinks = 0;
//
//            try {
//                WebElement systemErrorElement = driver.findElement(By.xpath("//*[contains(text(), 'システムエラーが発生しました')]"));
//                if (systemErrorElement != null && systemErrorElement.isDisplayed()) {
//                    hasSystemError = true;
//                    // Đếm số lượng link chứa text "トップページへ戻る"
//                    totalTopPageLinks = driver.findElements(By.xpath("//a[contains(text(), 'トップページへ戻る')]")).size();
//                    synchronized (this) {
//                        topPageCount += totalTopPageLinks; // Cộng vào số link top page
//                        updateStatus(String.format("Link %s hiển thị lỗi hệ thống. Số link トップページへ戻る: %d", url, totalTopPageLinks));
//                    }
//                }
//            } catch (Exception e) {
//                // Không tìm thấy text lỗi hệ thống
//            }
//
//            // Kiểm tra các trường hợp cần thiết
//            boolean foundExpectedMessage = false;
//            String currentUrl = driver.getCurrentUrl();
//
//            // Nếu đã tìm thấy lỗi hệ thống, đánh dấu là đã tìm thấy thông báo mong đợi
//            if (hasSystemError) {
//                foundExpectedMessage = true;
//            }
//
//            // Nếu chưa tìm thấy lỗi hệ thống, tiếp tục kiểm tra các trường hợp khác
//            if (!hasSystemError) {
//                try {
//                    // Đợi và kiểm tra thông báo "Không có điểm"
//                    WebElement noPointsMessage = wait.until(ExpectedConditions.presenceOfElementLocated(
//                            By.cssSelector("div.MuiAlert-standardError p.MuiTypography-body2")));
//                    if (noPointsMessage.getText().equals("移行可能なポイントがありません。")) {
//                        synchronized (this) {
//                            noPointsCount++;
//                            updateStatus(String.format("Không có điểm: %d", noPointsCount));
//                        }
//                        foundExpectedMessage = true;
//                    }
//                } catch (Exception e) {
//                    // Không tìm thấy thông báo "Không có điểm"
//                }
//
//                // Kiểm tra URL hoặc text cho trường hợp chuyển quà thành công
//                if (!foundExpectedMessage) {
//                    boolean hasTransferredUrl = currentUrl.contains("/point/charge/completion");
//                    boolean hasTransferredText = false;
//                    try {
//                        WebElement transferredMessage = wait.until(ExpectedConditions.presenceOfElementLocated(
//                                By.cssSelector("p.MuiTypography-body1")));
//                        hasTransferredText = transferredMessage.getText().equals("ギフトを移行しました");
//                    } catch (Exception e) {
//                        // Không tìm thấy text "ギフトを移行しました"
//                    }
//
//                    if (hasTransferredUrl || hasTransferredText) {
//                        synchronized (this) {
//                            giftTransferredCount++;
//                            updateStatus(String.format("Đã chuyển quà: %d", giftTransferredCount));
//                        }
//                        foundExpectedMessage = true;
//                    }
//                }
//
//                // Kiểm tra URL hoặc text cho trường hợp có nút quay về trang chủ
//                if (!foundExpectedMessage) {
//                    boolean hasTopPageUrl = currentUrl.contains("/oauth/authorization");
//                    boolean hasTopPageButton = false;
//                    try {
//                        List<WebElement> topPageButtons = driver.findElements(By.xpath("//a[contains(text(), 'トップページへ戻る')]"));
//                        hasTopPageButton = !topPageButtons.isEmpty();
//                        if (hasTopPageButton) {
//                            synchronized (this) {
//                                topPageCount += topPageButtons.size();
//                                updateStatus(String.format("Link %s có %d nút トップページへ戻る", url, topPageButtons.size()));
//                            }
//                        }
//                    } catch (Exception e) {
//                        // Không tìm thấy nút "トップページへ戻る"
//                    }
//
//                    if (hasTopPageUrl || hasTopPageButton) {
//                        foundExpectedMessage = true;
//                    }
//                }
//            }
//
//            // Cập nhật biến đếm
//            synchronized (this) {
//                if (foundExpectedMessage) {
//                    successCount++;
//                    updateProgress();
//                    updateStatus(String.format("Đăng nhập thành công! (Không có điểm: %d, Đã chuyển: %d, Top Page: %d)",
//                            noPointsCount, giftTransferredCount, topPageCount));
//                } else {
//                    failureCount++;
//                    failedUrls.add(url);
//                    updateProgress();
//                    Platform.runLater(
//                            () -> statusLabel.setText("Error: Link bị CAPTCHA hoặc không tìm thấy thông báo mong đợi"));
//                }
//            }
//        } catch (Exception e) {
//            synchronized (this) {
//                failureCount++;
//                failedUrls.add(url);
//                updateProgress();
//                Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage()));
//            }
//        } finally {
//            if (driver != null) {
//                try {
//                    driver.quit();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

    private void processUrl(String url) {
        WebDriver driver = null;
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-notifications", "--start-maximized", "--disable-gpu", "--no-sandbox",
                    "--disable-dev-shm-usage", "--disable-blink-features=AutomationControlled", "--disable-extensions",
                    "--disable-popup-blocking", "--disable-web-security", "--allow-running-insecure-content",
                    "--disable-site-isolation-trials", "--disable-features=IsolateOrigins,site-per-process",
                    "--disable-site-isolation-for-policy",
                    "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            options.setExperimentalOption("excludeSwitches", new String[] { "enable-automation" });
            options.setExperimentalOption("useAutomationExtension", false);

            driver = new ChromeDriver(options);
            addDriver(driver);
            driver.manage().window().maximize();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(120));

            // Random delay before processing URL
            Thread.sleep((long) (Math.random() * (MAX_DELAY - MIN_DELAY)) + MIN_DELAY);

            String username = usernameField.getText();
            String password = passwordField.getText();

            driver.get(url);
            updateStatus("Opening page: " + url);

            wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));
            Thread.sleep((long) (Math.random() * (MAX_DELAY - MIN_DELAY)) + MIN_DELAY);

            WebElement button = findButton(driver, wait);
            if (button == null) {
                throw new Exception("Button not found");
            }

            Thread.sleep((long) (Math.random() * (MAX_DELAY - MIN_DELAY)) + MIN_DELAY);
            clickButton(driver, button);

            updateStatus("Waiting for Line login form...");
            Thread.sleep(MIN_DELAY);

            if (!waitForLoginForm(driver)) {
                throw new Exception("Line login form not loaded");
            }

            WebElement emailInputField = driver.findElement(By.cssSelector("input[name='tid']"));
            WebElement passwordInputField = driver.findElement(By.cssSelector("input[name='tpasswd']"));

            emailInputField.sendKeys(username);
            Thread.sleep(MIN_DELAY);
            passwordInputField.sendKeys(password);
            Thread.sleep(MIN_DELAY);

            WebElement submitButton = driver.findElement(By.cssSelector("button.MdBtn01[type='submit']"));
            Thread.sleep(MIN_DELAY);
            submitButton.click();

            Thread.sleep(MAX_DELAY);
            Thread.sleep((long) (Math.random() * (MAX_DELAY - MIN_DELAY)) + MIN_DELAY);

            handlePostLogin(driver, url);

        } catch (Exception e) {
            synchronized (this) {
                failureCount++;
                failedUrls.add(url);
                updateProgress();
                Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage()));
            }
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private WebElement findButton(WebDriver driver, WebDriverWait wait) throws InterruptedException {
        WebElement button = null;
        try {
            WebElement section = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("section.css-14yxxc1")));
            if (section.isDisplayed()) {
                Thread.sleep(MIN_DELAY);
                WebElement heroSection = section.findElement(By.cssSelector("div[data-testid='signInPageHeroSection']"));
                if (heroSection.isDisplayed()) {
                    Thread.sleep(MIN_DELAY);
                    button = heroSection.findElement(By.xpath(".//button[contains(text(), 'ギフトをポイントに移行する')]"));
                }
            }
        } catch (Exception ignored) {}

        if (button == null) {
            try {
                Thread.sleep(MIN_DELAY);
                button = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//button[contains(text(), 'ギフトをポイントに移行する')]")));
            } catch (Exception ignored) {}
        }

        if (button == null) {
            try {
                Thread.sleep(MIN_DELAY);
                button = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//button[@data-gtm-click='shared-loginButton-loginButton' and contains(text(), 'ギフトをポイントに移行する')]")));
            } catch (Exception ignored) {}
        }

        if (button == null) {
            try {
                Thread.sleep(MIN_DELAY);
                button = (WebElement) ((JavascriptExecutor) driver).executeScript(
                        "return document.querySelector('button:contains(\"ギフトをポイントに移行する\")')");
            } catch (Exception ignored) {}
        }

        return button;
    }

    private void clickButton(WebDriver driver, WebElement button) throws Exception {
        try {
            button.click();
        } catch (Exception e1) {
            try {
                Thread.sleep(MIN_DELAY);
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);
            } catch (Exception e2) {
                try {
                    Thread.sleep(MIN_DELAY);
                    new Actions(driver).moveToElement(button).click().perform();
                } catch (Exception e3) {
                    try {
                        Thread.sleep(MIN_DELAY);
                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", button);
                        Thread.sleep(MIN_DELAY);
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);
                    } catch (Exception e4) {
                        throw new Exception("Unable to click button");
                    }
                }
            }
        }
    }

    private boolean waitForLoginForm(WebDriver driver) throws InterruptedException {
        boolean loginFormLoaded = false;
        for (int attempt = 0; attempt < 10 && !loginFormLoaded; attempt++) {
            try {
                WebElement emailInput = driver.findElement(By.cssSelector("input[name='tid']"));
                WebElement passwordInput = driver.findElement(By.cssSelector("input[name='tpasswd']"));
                if (emailInput.isDisplayed() && passwordInput.isDisplayed()) {
                    loginFormLoaded = true;
                }
            } catch (Exception e) {
                Thread.sleep(MIN_DELAY);
            }
        }
        return loginFormLoaded;
    }

    private void handlePostLogin(WebDriver driver, String url) throws Exception {
        boolean hasSystemError = false;
        int totalTopPageLinks = 0;

        try {
            WebElement systemErrorElement = driver.findElement(By.xpath("//*[contains(text(), 'システムエラーが発生しました')]"));
            if (systemErrorElement != null && systemErrorElement.isDisplayed()) {
                hasSystemError = true;
                totalTopPageLinks = driver.findElements(By.xpath("//a[contains(text(), 'トップページへ戻る')]")).size();
                synchronized (this) {
                    topPageCount += totalTopPageLinks;
                    updateStatus(String.format("Link %s shows system error. Top page links: %d", url, totalTopPageLinks));
                }
            }
        } catch (Exception ignored) {}

        boolean foundExpectedMessage = hasSystemError;
        String currentUrl = driver.getCurrentUrl();

        if (!hasSystemError) {
            try {
                WebElement noPointsMessage = new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.MuiAlert-standardError p.MuiTypography-body2")));
                if (noPointsMessage.getText().equals("移行可能なポイントがありません。")) {
                    synchronized (this) {
                        noPointsCount++;
                        updateStatus(String.format("No points: %d", noPointsCount));
                    }
                    foundExpectedMessage = true;
                }
            } catch (Exception ignored) {}

            if (!foundExpectedMessage) {
                boolean hasTransferredUrl = currentUrl.contains("/point/charge/completion");
                boolean hasTransferredText = false;
                try {
                    WebElement transferredMessage = new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                            ExpectedConditions.presenceOfElementLocated(By.cssSelector("p.MuiTypography-body1")));
                    hasTransferredText = transferredMessage.getText().equals("ギフトを移行しました");
                } catch (Exception ignored) {}

                if (hasTransferredUrl || hasTransferredText) {
                    synchronized (this) {
                        giftTransferredCount++;
                        updateStatus(String.format("Gift transferred: %d", giftTransferredCount));
                    }
                    foundExpectedMessage = true;
                }
            }

            if (!foundExpectedMessage) {
                boolean hasTopPageUrl = currentUrl.contains("/oauth/authorization");
                boolean hasTopPageButton = false;
                try {
                    List<WebElement> topPageButtons = driver.findElements(By.xpath("//a[contains(text(), 'トップページへ戻る')]"));
                    hasTopPageButton = !topPageButtons.isEmpty();
                    if (hasTopPageButton) {
                        synchronized (this) {
                            topPageCount += topPageButtons.size();
                            updateStatus(String.format("Link %s has %d top page buttons", url, topPageButtons.size()));
                        }
                    }
                } catch (Exception ignored) {}

                if (hasTopPageUrl || hasTopPageButton) {
                    foundExpectedMessage = true;
                }
            }
        }

        synchronized (this) {
            if (foundExpectedMessage) {
                successCount++;
                updateProgress();
                updateStatus(String.format("Login successful! (No points: %d, Transferred: %d, Top Page: %d)",
                        noPointsCount, giftTransferredCount, topPageCount));
            } else {
                failureCount++;
                failedUrls.add(url);
                updateProgress();
                Platform.runLater(() -> statusLabel.setText("Error: CAPTCHA or expected message not found"));
            }
        }
    }
}
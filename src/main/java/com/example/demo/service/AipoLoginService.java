package com.example.demo.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AipoLoginService {

    private static final String AIPO_LOGIN_URL = "https://apps.uniss.co.jp/aipo/";
    private static final String AIPO_LOGOUT_URL = "https://apps.uniss.co.jp/aipo/portal?action=ALJLogoutUser";
    private static final long LOGIN_TIMEOUT_SECONDS = 30L;
    private static final Pattern URL_PATTERN = Pattern.compile("showDialog\\s*\\(\\s*['\"]([^'\"]+?)(?:%27|['\"])[,)]");

    private final ReportService reportService;
    private final Map<String, WebDriver> userSessions = new ConcurrentHashMap<>();


    private static String safe(String value) {
        return value != null ? value : "";
    }

    @PostConstruct
    public void init() {
        WebDriverManager.chromedriver().setup();
    }

    private String extractUrlFromJavascript(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String decodedHref = href
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        log.info("Extracting URL from: {}", decodedHref);
        Matcher match = URL_PATTERN.matcher(decodedHref);
        String url = match.find() ? match.group(1) : null;
        log.info("Extracted URL: {}", url);
        return url;
    }

    private String extractPortletId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile("js_peid/([^?/]+)");
        Matcher match = pattern.matcher(url);
        String portletId = match.find() ? match.group(1) : null;
        if (portletId != null) {
            log.info("Extracted portlet ID: {} from URL: {}", portletId, url);
        }
        return portletId;
    }

    private void closeAnyOpenModals(WebDriver driver) {
        try {
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            List<WebElement> overlays = driver.findElements(By.cssSelector(".modalDialogUnderlayWrapper, .dijitDialogUnderlay"));
            if (overlays.stream().anyMatch(WebElement::isDisplayed)) {
                log.info("Found modal overlay, attempting to close...");
                List<WebElement> closeButtons = driver.findElements(
                        By.cssSelector(
                                ".dijitDialogCloseIcon, " +
                                        ".closeDialog, " +
                                        "button[name='ajaxbuttonClose'], " +
                                        "input[name='ajaxbuttonClose'], " +
                                        ".auiPopupButtons input[value='閉じる'], " +
                                        ".auiPopupButtons button[value='閉じる']"
                        )
                );

                for (WebElement btn : closeButtons) {
                    if (btn.isDisplayed()) {
                        try {
                            btn.click();
                            log.info("Clicked close button to dismiss modal");
                            Thread.sleep(500);
                            break;
                        } catch (Exception e) {
                            jsExecutor.executeScript("arguments[0].click();", btn);
                            Thread.sleep(500);
                            break;
                        }
                    }
                }

                List<WebElement> stillOpen = driver.findElements(By.cssSelector(".modalDialogUnderlayWrapper, .dijitDialogUnderlay"));
                if (stillOpen.stream().anyMatch(WebElement::isDisplayed)) {
                    log.info("Modal still open, sending Escape key...");
                    driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
                    Thread.sleep(500);
                }

                List<WebElement> finalCheck = driver.findElements(By.cssSelector(".modalDialogUnderlayWrapper"));
                if (finalCheck.stream().anyMatch(WebElement::isDisplayed)) {
                    log.info("Hiding modal via JavaScript...");
                    jsExecutor.executeScript(
                            "var overlays = document.querySelectorAll('.modalDialogUnderlayWrapper, .dijitDialogUnderlay');" +
                                    "overlays.forEach(function(el) { el.style.display = 'none'; });" +
                                    "var dialogs = document.querySelectorAll('.dijitDialog');" +
                                    "dialogs.forEach(function(el) { el.style.display = 'none'; });"
                    );
                    Thread.sleep(300);
                }
            }
        } catch (Exception e) {
            log.warn("Error while trying to close modals: {}", e.getMessage());
        }
    }

    public AipoLoginResult login(
            String username,
            String aipoUsername,
            String aipoPassword,
            String yearMonth,
            String timesheetFilePath,
            boolean autoSubmit
    ) {
        WebDriver driver = null;

        try {
            WebDriver existingDriver = userSessions.get(username);
            if (existingDriver != null) {
                try {
                    existingDriver.quit();
                } catch (Exception e) {
                    log.warn("Failed to close existing session for user: {}", username, e);
                }
            }

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--lang=ja");

            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(LOGIN_TIMEOUT_SECONDS));

            log.info("Navigating to Aipo login page for user: {}", username);
            driver.get(AIPO_LOGIN_URL);

            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("member_username")));

            WebElement usernameField = driver.findElement(By.id("member_username"));
            WebElement passwordField = driver.findElement(By.id("password"));

            usernameField.clear();
            usernameField.sendKeys(aipoUsername);
            passwordField.clear();
            passwordField.sendKeys(aipoPassword);

            log.info("Submitting login form for user: {}", username);
            WebElement loginButton = driver.findElement(By.cssSelector("button[type='submit'], input[type='submit'], .login-button, #loginButton"));
            loginButton.click();

            wait.until(d -> {
                String currentUrl = d.getCurrentUrl() != null ? d.getCurrentUrl() : "";
                return !currentUrl.toLowerCase().contains("login") ||
                        !d.findElements(By.cssSelector(".dashboard, .main-content, #main")).isEmpty();
            });

            String currentUrl = driver.getCurrentUrl();
            log.info("Login completed. Current URL: {}", currentUrl);

            List<WebElement> errorElements = driver.findElements(By.cssSelector(".error, .alert-danger, .login-error"));
            if (!errorElements.isEmpty() && errorElements.stream().anyMatch(WebElement::isDisplayed)) {
                String errorMessage = errorElements.stream().filter(WebElement::isDisplayed).findFirst().map(WebElement::getText).orElse("");
                driver.quit();
                return new AipoLoginResult(false, "ログインエラー: " + errorMessage, null, null, null,
                        false, false, null, false);
            }

            String workflowUrl = null;
            String createRequestUrl = null;
            boolean timesheetSelected = false;
            boolean fileUploaded = false;
            String uploadedFileName = null;
            AipoFormPreview formPreview = null;
            boolean autoSubmitted = false;

            try {
                closeAnyOpenModals(driver);

                List<WebElement> workflowLinks = driver.findElements(By.cssSelector(".auiPortletTitle a"));
                for (WebElement link : workflowLinks) {
                    String linkText = link.getText();
                    if (linkText.contains("ワークフロー")) {
                        workflowUrl = link.getDomAttribute("href");
                        log.info("Found workflow link: {}", workflowUrl);
                        break;
                    }
                }

                if (workflowUrl == null) {
                    List<WebElement> altLinks = driver.findElements(By.xpath("//a[contains(text(), 'ワークフロー')]"));
                    if (!altLinks.isEmpty()) {
                        workflowUrl = altLinks.getFirst().getDomAttribute("href");
                        log.info("Found workflow link (alt): {}", workflowUrl);
                    }
                }

                if (workflowUrl != null) {
                    log.info("Clicking workflow link to navigate to workflow page...");
                    JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

                    WebElement workflowLinkElement = driver.findElements(By.cssSelector(".auiPortletTitle a"))
                            .stream().filter(e -> e.getText().contains("ワークフロー")).findFirst().orElse(null);

                    if (workflowLinkElement != null) {
                        try {
                            workflowLinkElement.click();
                            log.info("Clicked workflow link (normal click)");
                        } catch (org.openqa.selenium.ElementClickInterceptedException e) {
                            log.warn("Click intercepted, trying to close modal and retry...");
                            closeAnyOpenModals(driver);
                            Thread.sleep(500);
                            try {
                                workflowLinkElement.click();
                                log.info("Clicked workflow link after closing modal");
                            } catch (Exception e2) {
                                jsExecutor.executeScript("arguments[0].click();", workflowLinkElement);
                                log.info("Clicked workflow link via JavaScript");
                            }
                        }
                        Thread.sleep(3000);
                    } else {
                        driver.get(workflowUrl);
                        log.info("Navigated to workflow URL directly");
                    }

                    try {
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".auiWidget, .auiButtonAction, a[title]")));
                    } catch (Exception e) {
                        log.warn("Timeout waiting for workflow page elements: {}", e.getMessage());
                    }

                    log.info("Searching for create request button to extract portlet ID...");
                    String portletId = null;

                    List<WebElement> createButtons = driver.findElements(By.cssSelector(".auiWidget a.auiButtonAction"));
                    log.info("Found {} buttons with .auiWidget a.auiButtonAction", createButtons.size());
                    for (WebElement btn : createButtons) {
                        String title = safe(btn.getDomAttribute("title"));
                        String text = btn.getText();
                        String href = safe(btn.getDomAttribute("href"));
                        log.info("Button: title='{}', text='{}', href='{}...'", title, text, href.length() > 100 ? href.substring(0, 100) : href);
                        if (title.contains("依頼を作成") || text.contains("依頼を作成")) {
                            createRequestUrl = extractUrlFromJavascript(href);
                            portletId = extractPortletId(createRequestUrl);
                            log.info("Found create request button (method 1): portletId={}", portletId);
                            break;
                        }
                    }

                    if (portletId == null) {
                        List<WebElement> altButtons = driver.findElements(By.xpath("//a[contains(@title, '依頼を作成') or contains(text(), '依頼を作成')]"));
                        log.info("Found {} buttons with XPath", altButtons.size());
                        if (!altButtons.isEmpty()) {
                            WebElement btn = altButtons.getFirst();
                            String href = safe(btn.getDomAttribute("href"));
                            createRequestUrl = extractUrlFromJavascript(href);
                            portletId = extractPortletId(createRequestUrl);
                            log.info("Found create request button (method 2): portletId={}", portletId);
                        }
                    }

                    if (portletId != null) {
                        try {
                            log.info("Executing JavaScript to open modal dialog with portletId: {}", portletId);
                            String showDialogScript =
                                    "aipo.common.showDialog(" +
                                            "'https://apps.uniss.co.jp/aipo/portal/js_peid/" + portletId + "?template=WorkflowFormScreen&entityid=new'," +
                                            "'" + portletId + "'," +
                                            "aipo.workflow.onLoadWorkflowDialog" +
                                            ");";
                            jsExecutor.executeScript(showDialogScript);
                            log.info("JavaScript executed: aipo.common.showDialog for portletId={}", portletId);

                            log.info("Waiting for modal dialog to appear...");
                            try {
                                wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("modalDialog")));
                                log.info("Modal dialog (id=modalDialog) is now visible");
                            } catch (Exception e) {
                                log.warn("Timeout waiting for modalDialog: {}", e.getMessage());
                            }

                            List<WebElement> modalDialogElements = driver.findElements(By.id("modalDialog"));
                            if (!modalDialogElements.isEmpty()) {
                                WebElement modalDialog = modalDialogElements.getFirst();
                                String displayStyle = modalDialog.getCssValue("display");
                                String opacity = modalDialog.getCssValue("opacity");
                                log.info("Modal dialog found - display: {}, opacity: {}", displayStyle, opacity);
                                if ("block".equals(displayStyle) && ("1".equals(opacity) || "1.0".equals(opacity))) {
                                    log.info("Modal dialog is fully visible and ready for interaction");
                                } else {
                                    log.warn("Modal dialog may not be fully visible yet, waiting...");
                                    Thread.sleep(2000);
                                }
                            } else {
                                log.warn("Modal dialog (id=modalDialog) not found");
                            }

                            try {
                                wait.until(ExpectedConditions.presenceOfElementLocated(By.id("category_id")));
                                log.info("Modal dialog loaded - category_id found");
                            } catch (Exception e) {
                                log.warn("Timeout waiting for category_id in modal: {}", e.getMessage());
                            }

                            List<WebElement> categoryElements = driver.findElements(By.id("category_id"));
                            if (!categoryElements.isEmpty()) {
                                WebElement categorySelect = categoryElements.getFirst();
                                Select select = new Select(categorySelect);
                                select.selectByValue("69");
                                log.info("Selected '勤務表' from category dropdown");
                                timesheetSelected = true;
                                Thread.sleep(2000);
                            } else {
                                log.warn("Category dropdown not found in modal");
                            }

                            try {
                                List<WebElement> noteElements = driver.findElements(By.id("workflow_Note"));
                                if (!noteElements.isEmpty()) {
                                    WebElement noteTextarea = noteElements.getFirst();
                                    noteTextarea.clear();
                                    noteTextarea.sendKeys("勤務表を提出します。");
                                    log.info("Entered text in workflow_Note textarea");
                                } else {
                                    log.warn("workflow_Note textarea not found in modal");
                                }
                            } catch (Exception e) {
                                log.warn("Failed to enter text in textarea: {}", e.getMessage());
                            }

                            try {
                                List<WebElement> isSavedRouteElements = driver.findElements(By.id("is_saved_route"));
                                if (!isSavedRouteElements.isEmpty()) {
                                    ((JavascriptExecutor) driver).executeScript(
                                            "arguments[0].value = 'TRUE';",
                                            isSavedRouteElements.getFirst()
                                    );
                                    log.info("Set is_saved_route to TRUE");
                                }

                                List<WebElement> routeSelectButton = driver.findElements(By.id("is_saved_route_button"));
                                if (!routeSelectButton.isEmpty() && routeSelectButton.getFirst().isDisplayed()) {
                                    String buttonValue = safe(routeSelectButton.getFirst().getDomProperty("value"));
                                    log.info("Route select button value: '{}'", buttonValue);
                                    if (!buttonValue.contains("ユーザー一覧から選択する")) {
                                        routeSelectButton.getFirst().click();
                                        Thread.sleep(1500);
                                        log.info("Clicked to switch to user list mode");
                                    }
                                }

                                Thread.sleep(1000);

                                List<WebElement> memberFromElements = driver.findElements(By.id("route_id"));
                                if (!memberFromElements.isEmpty()) {
                                    try {
                                        WebElement memberFromSelect = memberFromElements.getFirst();
                                        Select memberFromDropdown = new Select(memberFromSelect);
                                        jsExecutor.executeScript(
                                                "var select = arguments[0];" +
                                                        "select.value = '921';" +
                                                        "var event = new Event('change', { bubbles: true });" +
                                                        "select.dispatchEvent(event);",
                                                memberFromSelect
                                        );
                                        memberFromDropdown.selectByValue("921");
                                        log.info("Selected '921' via JavaScript and triggered change event");
                                        Thread.sleep(2000);
                                    } catch (Exception e) {
                                        log.warn("Failed to select member: {}", e.getMessage());
                                    }
                                } else {
                                    log.warn("Member from list (tmp_member_from) not found in modal");
                                }
                            } catch (Exception e) {
                                log.warn("Failed to set route: {}", e.getMessage());
                            }

                            String uploadFilePath = timesheetFilePath;
                            File tempFile = null;

                            if (uploadFilePath == null) {
                                try {
                                    int year;
                                    int month;
                                    if (yearMonth != null) {
                                        String[] parts = yearMonth.split("-");
                                        year = Integer.parseInt(parts[0]);
                                        month = Integer.parseInt(parts[1]);
                                    } else {
                                        LocalDate now = LocalDate.now();
                                        LocalDate targetMonth = now.minusMonths(1);
                                        year = targetMonth.getYear();
                                        month = targetMonth.getMonthValue();
                                    }

                                    LocalDate from = LocalDate.of(year, month, 1);
                                    LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

                                    log.info("Generating UNISS timesheet for {}: {} to {}", username, from, to);
                                    byte[] xlsxBytes = reportService.generateUnissXlsxBytes(username, from, to);

                                    String fileName = STR."\{from.getYear()}年\{String.format("%02d", from.getMonthValue())}月度UNISS勤務表(\{username}).xlsx";
                                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                                    tempFile = new File(tempDir, fileName);
                                    java.nio.file.Files.write(tempFile.toPath(), xlsxBytes);
                                    uploadFilePath = tempFile.getAbsolutePath();

                                    log.info("Generated UNISS timesheet: {}, saved to: {}", fileName, uploadFilePath);
                                } catch (Exception e) {
                                    log.warn("Failed to generate UNISS timesheet: {}", e.getMessage(), e);
                                }
                            }

                            if (uploadFilePath != null) {
                                try {
                                    File timesheetFile = new File(uploadFilePath);
                                    if (!timesheetFile.exists()) {
                                        log.warn("Timesheet file not found: {}", uploadFilePath);
                                    } else {
                                        List<WebElement> fileUploadButtons = new ArrayList<>();
                                        boolean inIframe = false;
                                        String uploadPortletId = portletId;

                                        log.info("Looking for file upload elements for workflow portlet: {}", portletId);

                                        String targetIframeId = "if_fileupload_" + portletId;
                                        List<WebElement> targetIframes = driver.findElements(By.id(targetIframeId));

                                        if (!targetIframes.isEmpty()) {
                                            WebElement iframe = targetIframes.getFirst();
                                            log.info("Found target iframe: {}, switching to iframe...", targetIframeId);
                                            driver.switchTo().frame(iframe);
                                            inIframe = true;

                                            fileUploadButtons = driver.findElements(By.cssSelector("[id^='fileuploadButton']"));
                                            if (!fileUploadButtons.isEmpty()) {
                                                String buttonId = safe(fileUploadButtons.getFirst().getDomAttribute("id"));
                                                log.info("Found file upload button in target iframe: {}", buttonId);
                                            }
                                        } else {
                                            log.info("Target iframe '{}' not found, checking all iframes...", targetIframeId);

                                            List<WebElement> allIframes = driver.findElements(By.cssSelector("iframe[id^='if_fileupload_']"));
                                            log.info("Found {} file upload iframe(s)", allIframes.size());

                                            for (WebElement iframe : allIframes) {
                                                String iframeId = safe(iframe.getDomAttribute("id"));
                                                log.info("Checking iframe: {}", iframeId);
                                                if (iframeId.contains(portletId)) {
                                                    log.info("Found matching iframe for workflow: {}", iframeId);
                                                    driver.switchTo().frame(iframe);
                                                    inIframe = true;

                                                    fileUploadButtons = driver.findElements(By.cssSelector("[id^='fileuploadButton']"));
                                                    if (!fileUploadButtons.isEmpty()) {
                                                        String buttonId = safe(fileUploadButtons.getFirst().getDomAttribute("id"));
                                                        log.info("Found file upload button: {}", buttonId);
                                                    }
                                                    break;
                                                }
                                            }

                                            if (!inIframe && !allIframes.isEmpty()) {
                                                WebElement iframe = allIframes.getFirst();
                                                String iframeId = safe(iframe.getDomAttribute("id"));
                                                log.warn("No matching iframe found, using first iframe: {}", iframeId);
                                                driver.switchTo().frame(iframe);
                                                inIframe = true;

                                                fileUploadButtons = driver.findElements(By.cssSelector("[id^='fileuploadButton']"));
                                                if (!fileUploadButtons.isEmpty()) {
                                                    String buttonId = safe(fileUploadButtons.getFirst().getDomAttribute("id"));
                                                    uploadPortletId = buttonId.replace("fileuploadButton", "").replace("global-", "");
                                                    log.info("Found file upload button in fallback iframe: {}, portletId: {}", buttonId, uploadPortletId);
                                                }
                                            }
                                        }

                                        if (!fileUploadButtons.isEmpty()) {
                                            WebElement fileUploadButton = fileUploadButtons.getFirst();
                                            log.info("Upload portlet ID: {}", uploadPortletId);

                                            List<WebElement> fileInputs = fileUploadButton.findElements(By.cssSelector("input[type='file']"));
                                            if (fileInputs.isEmpty()) {
                                                fileInputs = driver.findElements(By.id("attachment"));
                                            }

                                            if (!fileInputs.isEmpty()) {
                                                WebElement fileInput = fileInputs.getFirst();
                                                fileInput.sendKeys(timesheetFile.getAbsolutePath());
                                                log.info("Set file to input: {}", timesheetFile.getAbsolutePath());

                                                Thread.sleep(500);

                                                String triggerScript =
                                                        "(function() {" +
                                                                "var fileInput = document.querySelector(\"input[type='file'], #attachment\");" +
                                                                "if (!fileInput || !fileInput.files || fileInput.files.length === 0) { return 'NO_FILE'; }" +
                                                                "if (fileInput.onchange) { fileInput.onchange(); return 'ONCHANGE_CALLED:' + fileInput.files[0].name; }" +
                                                                "var event = new Event('change', { bubbles: true });" +
                                                                "fileInput.dispatchEvent(event);" +
                                                                "return 'EVENT_DISPATCHED:' + fileInput.files[0].name;" +
                                                                "})();";

                                                Object triggerResult = jsExecutor.executeScript(triggerScript);
                                                log.info("Trigger result (in iframe): {}", triggerResult);

                                                driver.switchTo().defaultContent();
                                                log.info("Switched back to main context after triggering change event");
                                                inIframe = false;

                                                Thread.sleep(5000);

                                                List<WebElement> attachmentList = driver.findElements(By.cssSelector("[id^='attachments_'] li"));
                                                if (!attachmentList.isEmpty()) {
                                                    WebElement first = attachmentList.getFirst();
                                                    String dataFilename = safe(first.getDomAttribute("data-filename"));
                                                    uploadedFileName = !dataFilename.isBlank()
                                                            ? dataFilename
                                                            : first.getText().replace("削除", "").replace("\u200B", "").trim();
                                                    fileUploaded = true;
                                                    log.info("File upload confirmed via attachment list: {}", uploadedFileName);
                                                } else {
                                                    log.warn("Attachment list is empty after upload attempt");
                                                    List<WebElement> folderNameInputs = driver.findElements(By.cssSelector("[id^='folderName_']"));
                                                    if (!folderNameInputs.isEmpty()) {
                                                        String folderValue = safe(folderNameInputs.getFirst().getDomProperty("value"));
                                                        if (!folderValue.isBlank()) {
                                                            fileUploaded = true;
                                                            uploadedFileName = timesheetFile.getName();
                                                            log.info("File upload confirmed via folderName input: {}, using filename: {}", folderValue, uploadedFileName);
                                                        }
                                                    }
                                                }
                                            } else {
                                                log.warn("File input not found in button or by id='attachment'");
                                            }
                                        } else {
                                            log.warn("File upload button not found in any context");
                                        }

                                        if (inIframe) {
                                            driver.switchTo().defaultContent();
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to upload: {}", e.getMessage(), e);
                                    try {
                                        driver.switchTo().defaultContent();
                                    } catch (Exception ignored) {
                                    }
                                } finally {
                                    if (tempFile != null) {
                                        try {
                                            java.nio.file.Files.deleteIfExists(tempFile.toPath());
                                        } catch (Exception e) {
                                            log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath(), e);
                                        }
                                    }
                                }
                            }

                            AipoFormPreview collectedFormPreview = collectFormPreview(driver);
                            if (uploadedFileName != null && !uploadedFileName.isBlank() &&
                                    (collectedFormPreview.attachedFileName() == null || collectedFormPreview.attachedFileName().isBlank())) {
                                formPreview = collectedFormPreview.withAttachedFileName(uploadedFileName);
                                log.info("Form preview - attached file updated from upload: {}", uploadedFileName);
                            } else {
                                formPreview = collectedFormPreview;
                            }

                            log.info("Form preview collected: {}", formPreview);

                            AipoFormPreview currentFormPreview = formPreview;
                            if (autoSubmit && currentFormPreview.ready()
                                    && currentFormPreview.submitButtonId() != null) {
                                try {
                                    log.info("Auto submit enabled. Clicking submit button: {}", currentFormPreview.submitButtonId());
                                    WebElement submitButton = driver.findElement(By.id(currentFormPreview.submitButtonId()));
                                    if (submitButton.isDisplayed()) {
                                        submitButton.click();
                                        log.info("Auto submit: Clicked submit button");
                                        Thread.sleep(3000);
                                        autoSubmitted = true;
                                    }
                                } catch (Exception e) {
                                    log.warn("Auto submit failed: {}", e.getMessage(), e);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to process modal dialog: {}", e.getMessage(), e);
                            formPreview = collectFormPreview(driver);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to find workflow link or create request button: {}", e.getMessage(), e);
            }

            String sessionId = "unknown";
            try {
                var remote = (org.openqa.selenium.remote.RemoteWebDriver) driver;
                sessionId = String.valueOf(remote.getSessionId());
            } catch (Exception ignored) {
            }
            userSessions.put(username, driver);

            log.info("Aipo login successful for user: {}, sessionId: {}, workflowUrl: {}, createRequestUrl: {}, timesheetSelected: {}, fileUploaded: {}, formReady: {}, autoSubmitted: {}",
                    username, sessionId, workflowUrl, createRequestUrl, timesheetSelected, fileUploaded,
                    formPreview != null ? formPreview.ready() : null, autoSubmitted);

            String message;
            if (autoSubmitted) {
                message = "Aipoへのログインに成功しました。申請が自動送信されました。";
            } else if (formPreview != null && formPreview.ready()) {
                message = "Aipoへのログインに成功しました。申請準備が完了しました。";
            } else if (fileUploaded) {
                message = "Aipoへのログインに成功しました。勤務表ファイルがアップロードされました。";
            } else if (timesheetSelected) {
                message = "Aipoへのログインに成功しました。勤務表が選択されました。";
            } else if (createRequestUrl != null) {
                message = "Aipoへのログインに成功しました。依頼作成ボタンが見つかりました。";
            } else if (workflowUrl != null) {
                message = "Aipoへのログインに成功しました。ワークフローが見つかりました。";
            } else {
                message = "Aipoへのログインに成功しました";
            }

            return new AipoLoginResult(
                    true,
                    message,
                    sessionId,
                    workflowUrl,
                    createRequestUrl,
                    timesheetSelected,
                    fileUploaded,
                    formPreview,
                    autoSubmitted
            );
        } catch (Exception e) {
            log.error("Aipo login failed for user: {}", username, e);
            if (driver != null) {
                driver.quit();
            }
            return new AipoLoginResult(false, "ログインに失敗しました: " + e.getMessage(), null, null, null,
                    false, false, null, false);
        }
    }

    public boolean logout(String username) {
        try {
            WebDriver driver = userSessions.remove(username);
            if (driver != null) {
                try {
                    driver.get(AIPO_LOGOUT_URL);
                    log.info("Aipo logout URL accessed for user: {}", username);
                } catch (Exception e) {
                    log.warn("Failed to access Aipo logout URL for user: {}", username, e);
                } finally {
                    driver.quit();
                }
                log.info("Aipo session closed for user: {}", username);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to logout Aipo session for user: {}", username, e);
            return false;
        }
    }

    public boolean isLoggedIn(String username) {
        if (!userSessions.containsKey(username)) {
            return false;
        }
        try {
            WebDriver driver = userSessions.get(username);
            return driver != null && driver.getCurrentUrl() != null;
        } catch (Exception e) {
            userSessions.remove(username);
            return false;
        }
    }

    public Map.Entry<Boolean, String> submitRequest(String username, String submitButtonId) {
        WebDriver driver = userSessions.get(username);
        if (driver == null) {
            log.warn("No session found for user: {}", username);
            return Map.entry(false, "セッションが見つかりません。再度ログインしてください。");
        }

        try {
            log.info("Submitting request for user: {} with button ID: {}", username, submitButtonId);
            List<WebElement> submitButtons = driver.findElements(By.id(submitButtonId));
            if (submitButtons.isEmpty() || !submitButtons.getFirst().isDisplayed()) {
                log.warn("Submit button not found or not visible: {}", submitButtonId);
                return Map.entry(false, "申請ボタンが見つかりません。Aipo画面を確認してください。");
            }

            WebElement submitButton = submitButtons.getFirst();
            submitButton.click();
            log.info("Clicked submit button: {}", submitButtonId);
            Thread.sleep(3000);

            List<WebElement> successElements = driver.findElements(By.cssSelector(".success, .alert-success, .message-success"));
            if (!successElements.isEmpty() && successElements.stream().anyMatch(WebElement::isDisplayed)) {
                String successMessage = successElements.stream().filter(WebElement::isDisplayed).findFirst().map(WebElement::getText).orElse("");
                log.info("Submit successful: {}", successMessage);
                return Map.entry(true, "申請が完了しました: " + successMessage);
            }

            List<WebElement> errorElements = driver.findElements(By.cssSelector(".error, .alert-danger, .message-error"));
            if (!errorElements.isEmpty() && errorElements.stream().anyMatch(WebElement::isDisplayed)) {
                String errorMessage = errorElements.stream().filter(WebElement::isDisplayed).findFirst().map(WebElement::getText).orElse("");
                log.warn("Submit error: {}", errorMessage);
                return Map.entry(false, "申請エラー: " + errorMessage);
            }

            log.info("Submit completed (no message found)");
            return Map.entry(true, "申請が完了しました");
        } catch (Exception e) {
            log.error("Failed to submit request for user: {}", username, e);
            return Map.entry(false, "申請に失敗しました: " + e.getMessage());
        }
    }

    private AipoFormPreview collectFormPreview(WebDriver driver) {
        String category = null;
        String note = null;
        List<String> routeMembers = new ArrayList<>();
        String attachedFileName = null;
        String submitButtonId = null;
        String fileUploadButtonId = null;
        boolean fileUploadButtonExists = false;
        boolean fileInputExists = false;

        try {
            try {
                WebElement categorySelect = driver.findElement(By.id("category_id"));
                Select select = new Select(categorySelect);
                category = select.getFirstSelectedOption().getText();
                log.info("Form preview - category: {}", category);
            } catch (Exception e) {
                log.warn("Failed to get category: {}", e.getMessage());
            }

            try {
                WebElement noteTextarea = driver.findElement(By.id("workflow_Note"));
                note = noteTextarea.getDomProperty("value");
                log.info("Form preview - note: {}", note);
            } catch (Exception e) {
                log.warn("Failed to get note: {}", e.getMessage());
            }

            try {
                WebElement positionsSelect = driver.findElement(By.id("positions"));
                List<WebElement> options = positionsSelect.findElements(By.tagName("option"));
                for (WebElement option : options) {
                    String memberName = option.getText();
                    if (!memberName.isBlank()) {
                        routeMembers.add(memberName);
                    }
                }
                log.info("Form preview - route members: {}", routeMembers);
            } catch (Exception e) {
                log.warn("Failed to get route members: {}", e.getMessage());
            }

            try {
                List<WebElement> attachmentLists = driver.findElements(By.cssSelector("[id^='attachments_']"));
                log.info("Form preview - found {} attachment list(s)", attachmentLists.size());
                for (WebElement list : attachmentLists) {
                    String listId = safe(list.getDomAttribute("id"));
                    List<WebElement> listItems = list.findElements(By.tagName("li"));
                    log.info("Form preview - attachment list '{}' has {} item(s)", listId, listItems.size());
                }

                List<WebElement> attachmentList = driver.findElements(By.cssSelector("[id^='attachments_'] li, .attachments li"));
                log.info("Form preview - total attachment items found: {}", attachmentList.size());

                if (!attachmentList.isEmpty()) {
                    WebElement firstAttachment = attachmentList.getFirst();
                    String dataFileId = safe(firstAttachment.getDomAttribute("data-fileid"));
                    String dataFilename = safe(firstAttachment.getDomAttribute("data-filename"));
                    String liText = firstAttachment.getText();
                    log.info("Form preview - first attachment: data-fileid='{}', data-filename='{}', text='{}'",
                            dataFileId, dataFilename, liText);

                    if (!dataFilename.isBlank()) {
                        attachedFileName = dataFilename;
                    } else {
                        List<WebElement> spanElements = firstAttachment.findElements(By.tagName("span"));
                        if (!spanElements.isEmpty()) {
                            WebElement fileNameSpan = spanElements.stream().filter(span ->
                                    !safe(span.getDomAttribute("class")).contains("deletebutton")
                            ).findFirst().orElse(null);
                            if (fileNameSpan != null) {
                                attachedFileName = fileNameSpan.getText().replace("\u200B", "").trim();
                            }
                        }
                        if (attachedFileName == null || attachedFileName.isBlank()) {
                            attachedFileName = firstAttachment.getText().replace("削除", "")
                                    .replace("\u200B", "").trim();
                        }
                    }
                    log.info("Form preview - attached file: {}", attachedFileName);
                }

                List<WebElement> allFolderNameInputs = driver.findElements(By.cssSelector("[id^='folderName_']"));
                log.info("Form preview - found {} folderName input(s)", allFolderNameInputs.size());
                for (WebElement input : allFolderNameInputs) {
                    String folderId = safe(input.getDomAttribute("id"));
                    String folderValue = safe(input.getDomProperty("value"));
                    log.info("Form preview - folderName: id='{}', value='{}'", folderId, folderValue);
                }
            } catch (Exception e) {
                log.warn("Failed to get attached file: {}", e.getMessage());
            }

            try {
                List<WebElement> submitButtons = driver.findElements(By.cssSelector("input[id^='al_submit_']"));
                if (!submitButtons.isEmpty()) {
                    submitButtonId = submitButtons.getFirst().getDomAttribute("id");
                    log.info("Form preview - submit button ID: {}", submitButtonId);
                }
            } catch (Exception e) {
                log.warn("Failed to get submit button ID: {}", e.getMessage());
            }

            try {
                List<WebElement> fileUploadButtons = driver.findElements(By.cssSelector("[id^='fileuploadButton']"));
                if (!fileUploadButtons.isEmpty()) {
                    WebElement fileUploadButton = fileUploadButtons.getFirst();
                    fileUploadButtonId = fileUploadButton.getDomAttribute("id");
                    fileUploadButtonExists = fileUploadButton.isDisplayed();
                    log.info("Form preview - file upload button ID: {}, exists: {}", fileUploadButtonId, fileUploadButtonExists);

                    List<WebElement> fileInputs = fileUploadButton.findElements(By.cssSelector("input[type='file']"));
                    if (!fileInputs.isEmpty()) {
                        fileInputExists = true;
                        log.info("Form preview - file input exists: true");
                    } else {
                        log.warn("Form preview - file input NOT found inside upload button");
                    }
                } else {
                    log.warn("Form preview - file upload button NOT found in main context");
                }

                List<WebElement> attachmentInputs = driver.findElements(By.id("attachment"));
                if (!attachmentInputs.isEmpty() && attachmentInputs.getFirst().isDisplayed()) {
                    fileInputExists = true;
                    log.info("Form preview - attachment input (id='attachment') found and displayed in main context");
                } else if (!attachmentInputs.isEmpty()) {
                    fileInputExists = true;
                    log.info("Form preview - attachment input (id='attachment') found (hidden but exists) in main context");
                }

                if (!fileUploadButtonExists || !fileInputExists) {
                    List<WebElement> fileUploadIframes = driver.findElements(By.cssSelector("iframe[id^='if_fileupload_']"));
                    if (!fileUploadIframes.isEmpty()) {
                        WebElement iframe = fileUploadIframes.getFirst();
                        String iframeId = safe(iframe.getDomAttribute("id"));
                        log.info("Form preview - Found file upload iframe: {}, switching to iframe...", iframeId);

                        try {
                            driver.switchTo().frame(iframe);

                            List<WebElement> iframeFileUploadButtons = driver.findElements(By.cssSelector("[id^='fileuploadButton']"));
                            if (!iframeFileUploadButtons.isEmpty()) {
                                WebElement btn = iframeFileUploadButtons.getFirst();
                                fileUploadButtonId = btn.getDomAttribute("id");
                                fileUploadButtonExists = true;
                                log.info("Form preview - file upload button found in iframe: {}", fileUploadButtonId);
                            }

                            List<WebElement> iframeAttachmentInputs = driver.findElements(By.id("attachment"));
                            if (!iframeAttachmentInputs.isEmpty()) {
                                fileInputExists = true;
                                log.info("Form preview - attachment input (id='attachment') found in iframe");
                            }

                            List<WebElement> iframeFileInputs = driver.findElements(By.cssSelector("input[type='file']"));
                            if (!iframeFileInputs.isEmpty()) {
                                fileInputExists = true;
                                log.info("Form preview - file input found in iframe");
                            }
                        } finally {
                            driver.switchTo().defaultContent();
                            log.info("Form preview - Switched back to main context");
                        }
                    } else {
                        log.warn("Form preview - No file upload iframe found");
                    }
                }

                if (!fileUploadButtonExists && !fileInputExists) {
                    log.warn("Form preview - attachment input (id='attachment') NOT found in any context");
                }
            } catch (Exception e) {
                log.warn("Failed to check file upload button: {}", e.getMessage());
                try {
                    driver.switchTo().defaultContent();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.warn("Error collecting form preview: {}", e.getMessage());
        }

        boolean isReady = category != null && !category.isBlank() && !"未分類".equals(category) &&
                note != null && !note.isBlank() && !routeMembers.isEmpty() && submitButtonId != null;

        return new AipoFormPreview(
                category,
                note,
                routeMembers,
                attachedFileName,
                submitButtonId,
                fileUploadButtonId,
                fileUploadButtonExists,
                fileInputExists,
                isReady
        );
    }

    public void cleanupAllSessions() {
        userSessions.forEach((username, driver) -> {
            try {
                driver.quit();
                log.info("Cleaned up Aipo session for user: {}", username);
            } catch (Exception e) {
                log.warn("Failed to cleanup session for user: {}", username, e);
            }
        });
        userSessions.clear();
    }
}

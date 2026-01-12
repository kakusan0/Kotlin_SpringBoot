package com.example.demo.service

import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

data class AipoLoginResult(
    val success: Boolean,
    val message: String,
    val sessionId: String? = null,
    val workflowUrl: String? = null,
    val createRequestUrl: String? = null,
    val timesheetSelected: Boolean = false,
    val fileUploaded: Boolean = false,
    val formPreview: AipoFormPreview? = null,
    val autoSubmitted: Boolean = false
)

/**
 * 入力フォームのプレビュー情報
 */
data class AipoFormPreview(
    val category: String? = null,
    val note: String? = null,
    val routeMembers: List<String> = emptyList(),
    val attachedFileName: String? = null,
    val submitButtonId: String? = null,
    val fileUploadButtonId: String? = null,
    val fileUploadButtonExists: Boolean = false,
    val fileInputExists: Boolean = false,
    val isReady: Boolean = false
)

@Service
class AipoLoginService(
    private val reportService: ReportService
) {

    private val logger = LoggerFactory.getLogger(AipoLoginService::class.java)

    // ユーザーごとにWebDriverセッションを保持
    private val userSessions = ConcurrentHashMap<String, WebDriver>()

    companion object {
        private const val AIPO_LOGIN_URL = "https://apps.uniss.co.jp/aipo/"
        private const val AIPO_LOGOUT_URL = "https://apps.uniss.co.jp/aipo/portal?action=ALJLogoutUser"
        private const val LOGIN_TIMEOUT_SECONDS = 30L

        // javascript:aipo.common.showDialog('URL',...) からURLを抽出
        // %27 はURLエンコードされたシングルクォートなので、その前までを抽出
        private val URL_PATTERN = Regex("""showDialog\s*\(\s*['"]([^'"]+?)(?:%27|['"])[,)]""")
    }

    init {
        // WebDriverManagerでChromeDriverをセットアップ
        WebDriverManager.chromedriver().setup()
    }

    /**
     * javascript:href からURLを抽出する
     */
    private fun extractUrlFromJavascript(href: String): String? {
        if (href.isBlank()) return null

        // HTMLエンティティをデコード
        val decodedHref = href
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        logger.info("Extracting URL from: $decodedHref")

        // javascript:aipo.common.showDialog('https://...', ...) からURLを抽出
        val match = URL_PATTERN.find(decodedHref)
        val url = match?.groupValues?.get(1)

        logger.info("Extracted URL: $url")
        return url
    }

    /**
     * URLからポートレットIDを抽出する
     * 例: https://apps.uniss.co.jp/aipo/portal/js_peid/P-195e5838221-10004?action=... から P-195e5838221-10004 を抽出
     */
    private fun extractPortletId(url: String?): String? {
        if (url.isNullOrBlank()) return null

        // js_peid/の後のIDを抽出
        val pattern = Regex("""js_peid/([^?/]+)""")
        val match = pattern.find(url)
        val portletId = match?.groupValues?.get(1)

        if (portletId != null) {
            logger.info("Extracted portlet ID: $portletId from URL: $url")
        }
        return portletId
    }

    /**
     * 開いているモーダルダイアログを閉じる
     */
    private fun closeAnyOpenModals(driver: WebDriver) {
        try {
            val jsExecutor = driver as org.openqa.selenium.JavascriptExecutor

            // モーダルオーバーレイが表示されているか確認
            val overlays = driver.findElements(By.cssSelector(".modalDialogUnderlayWrapper, .dijitDialogUnderlay"))
            if (overlays.any { it.isDisplayed }) {
                logger.info("Found modal overlay, attempting to close...")

                // 閉じるボタンを探してクリック
                val closeButtons = driver.findElements(
                    By.cssSelector(
                        ".dijitDialogCloseIcon, " +
                                ".closeDialog, " +
                                "button[name='ajaxbuttonClose'], " +
                                "input[name='ajaxbuttonClose'], " +
                                ".auiPopupButtons input[value='閉じる'], " +
                                ".auiPopupButtons button[value='閉じる']"
                    )
                )

                for (btn in closeButtons) {
                    if (btn.isDisplayed) {
                        try {
                            btn.click()
                            logger.info("Clicked close button to dismiss modal")
                            Thread.sleep(500)
                            break
                        } catch (e: Exception) {
                            // JavaScriptでクリック
                            jsExecutor.executeScript("arguments[0].click();", btn)
                            Thread.sleep(500)
                            break
                        }
                    }
                }

                // それでもモーダルが開いている場合、Escapeキーを送信
                val stillOpen = driver.findElements(By.cssSelector(".modalDialogUnderlayWrapper, .dijitDialogUnderlay"))
                if (stillOpen.any { it.isDisplayed }) {
                    logger.info("Modal still open, sending Escape key...")
                    driver.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ESCAPE)
                    Thread.sleep(500)
                }

                // JavaScriptでモーダルを非表示にする
                val finalCheck = driver.findElements(By.cssSelector(".modalDialogUnderlayWrapper"))
                if (finalCheck.any { it.isDisplayed }) {
                    logger.info("Hiding modal via JavaScript...")
                    jsExecutor.executeScript(
                        """
                        var overlays = document.querySelectorAll('.modalDialogUnderlayWrapper, .dijitDialogUnderlay');
                        overlays.forEach(function(el) { el.style.display = 'none'; });
                        var dialogs = document.querySelectorAll('.dijitDialog');
                        dialogs.forEach(function(el) { el.style.display = 'none'; });
                    """
                    )
                    Thread.sleep(300)
                }
            }
        } catch (e: Exception) {
            logger.warn("Error while trying to close modals: ${e.message}")
        }
    }

    /**
     * Aipoにログインする
     * @param username ユーザー名（セッション管理用）
     * @param aipoUsername Aipoのユーザー名
     * @param aipoPassword Aipoのパスワード
     * @param timesheetFilePath アップロードする勤務表ファイルのパス（省略可）
     * @param autoSubmit ファイルアップロード後に自動で申請を送信するか（デフォルト: false）
     */
    fun login(
        username: String,
        aipoUsername: String,
        aipoPassword: String,
        timesheetFilePath: String? = null,
        autoSubmit: Boolean = false
    ): AipoLoginResult {
        var driver: WebDriver? = null

        try {
            // 既存セッションがあれば閉じる
            userSessions[username]?.let { existingDriver ->
                try {
                    existingDriver.quit()
                } catch (e: Exception) {
                    logger.warn("Failed to close existing session for user: $username", e)
                }
            }

            // Chrome オプション設定
            val options = ChromeOptions().apply {
                addArguments("--headless=new") // ヘッドレスモード
                addArguments("--no-sandbox")
                addArguments("--disable-dev-shm-usage")
                addArguments("--disable-gpu")
                addArguments("--window-size=1920,1080")
                addArguments("--remote-allow-origins=*")
                // 日本語対応
                addArguments("--lang=ja")
            }

            driver = ChromeDriver(options)
            val wait = WebDriverWait(driver, Duration.ofSeconds(LOGIN_TIMEOUT_SECONDS))

            logger.info("Navigating to Aipo login page for user: $username")

            // Aipoログインページにアクセス
            driver.get(AIPO_LOGIN_URL)

            // ログインフォームが表示されるまで待機
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("member_username")))

            // ユーザー名とパスワードを入力
            val usernameField = driver.findElement(By.id("member_username"))
            val passwordField = driver.findElement(By.id("password"))

            usernameField.clear()
            usernameField.sendKeys(aipoUsername)

            passwordField.clear()
            passwordField.sendKeys(aipoPassword)

            logger.info("Submitting login form for user: $username")

            // ログインボタンをクリック
            val loginButton =
                driver.findElement(By.cssSelector("button[type='submit'], input[type='submit'], .login-button, #loginButton"))
            loginButton.click()

            // ログイン成功を待機（URLが変わるかダッシュボードが表示されるまで）
            wait.until { d ->
                val currentUrl = d.currentUrl ?: ""
                !currentUrl.contains("login", ignoreCase = true) ||
                        d.findElements(By.cssSelector(".dashboard, .main-content, #main")).isNotEmpty()
            }

            // ログイン後のURLを確認
            val currentUrl = driver.currentUrl
            logger.info("Login completed. Current URL: $currentUrl")

            // エラーメッセージがないか確認
            val errorElements = driver.findElements(By.cssSelector(".error, .alert-danger, .login-error"))
            if (errorElements.isNotEmpty() && errorElements.any { it.isDisplayed }) {
                val errorMessage = errorElements.first { it.isDisplayed }.text
                driver.quit()
                return AipoLoginResult(
                    success = false,
                    message = "ログインエラー: $errorMessage"
                )
            }

            // ワークフローリンクを探す
            var workflowUrl: String? = null
            var createRequestUrl: String? = null
            var timesheetSelected = false
            var fileUploaded = false
            var uploadedFileName: String? = null
            var formPreview: AipoFormPreview? = null
            var autoSubmitted = false
            try {
                // モーダルダイアログが開いている場合は閉じる
                closeAnyOpenModals(driver)

                // .auiPortletTitle 内のワークフローリンクを探す
                val workflowLinks = driver.findElements(By.cssSelector(".auiPortletTitle a"))
                for (link in workflowLinks) {
                    val linkText = link.text
                    if (linkText.contains("ワークフロー")) {
                        workflowUrl = link.getAttribute("href")
                        logger.info("Found workflow link: $workflowUrl")
                        break
                    }
                }

                // 見つからない場合は別のセレクターで試す
                if (workflowUrl == null) {
                    val altLinks = driver.findElements(By.xpath("//a[contains(text(), 'ワークフロー')]"))
                    if (altLinks.isNotEmpty()) {
                        workflowUrl = altLinks.first().getAttribute("href")
                        logger.info("Found workflow link (alt): $workflowUrl")
                    }
                }

                // ワークフローURLが見つかった場合、ワークフローリンクをクリック
                if (workflowUrl != null) {
                    logger.info("Clicking workflow link to navigate to workflow page...")

                    // JavaScriptでクリック（オーバーレイがあっても動作する）
                    val jsExecutor = driver as org.openqa.selenium.JavascriptExecutor

                    // ワークフローリンクを再度探してクリック
                    val workflowLinkElement = driver.findElements(By.cssSelector(".auiPortletTitle a"))
                        .find { it.text.contains("ワークフロー") }

                    if (workflowLinkElement != null) {
                        try {
                            // 通常のクリックを試みる
                            workflowLinkElement.click()
                            logger.info("Clicked workflow link (normal click)")
                        } catch (e: org.openqa.selenium.ElementClickInterceptedException) {
                            // クリックがインターセプトされた場合、モーダルを閉じて再試行
                            logger.warn("Click intercepted, trying to close modal and retry...")
                            closeAnyOpenModals(driver)
                            Thread.sleep(500)

                            try {
                                workflowLinkElement.click()
                                logger.info("Clicked workflow link after closing modal")
                            } catch (e2: Exception) {
                                // それでもダメな場合はJavaScriptでクリック
                                jsExecutor.executeScript("arguments[0].click();", workflowLinkElement)
                                logger.info("Clicked workflow link via JavaScript")
                            }
                        }

                        // ページ遷移を待機
                        Thread.sleep(3000)
                    } else {
                        // リンク要素が見つからない場合はURLに直接アクセス
                        driver.get(workflowUrl)
                        logger.info("Navigated to workflow URL directly")
                    }

                    // ワークフロー画面の読み込みを待機
                    try {
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".auiWidget, .auiButtonAction, a[title]")))
                    } catch (e: Exception) {
                        logger.warn("Timeout waiting for workflow page elements: ${e.message}")
                    }

                    // 「依頼を作成する」ボタンを探してポートレットIDを取得
                    logger.info("Searching for create request button to extract portlet ID...")

                    var portletId: String? = null

                    // 方法1: .auiWidget a.auiButtonAction から探す
                    val createButtons = driver.findElements(By.cssSelector(".auiWidget a.auiButtonAction"))
                    logger.info("Found ${createButtons.size} buttons with .auiWidget a.auiButtonAction")
                    for (btn in createButtons) {
                        val title = btn.getAttribute("title") ?: ""
                        val text = btn.text
                        val href = btn.getAttribute("href") ?: ""
                        logger.info("Button: title='$title', text='$text', href='${href.take(100)}...'")
                        if (title.contains("依頼を作成") || text.contains("依頼を作成")) {
                            createRequestUrl = extractUrlFromJavascript(href)
                            portletId = extractPortletId(createRequestUrl)
                            logger.info("Found create request button (method 1): portletId=$portletId")
                            break
                        }
                    }

                    // 方法2: XPathで探す
                    if (portletId == null) {
                        val altButtons =
                            driver.findElements(By.xpath("//a[contains(@title, '依頼を作成') or contains(text(), '依頼を作成')]"))
                        logger.info("Found ${altButtons.size} buttons with XPath")
                        if (altButtons.isNotEmpty()) {
                            val btn = altButtons.first()
                            val href = btn.getAttribute("href") ?: ""
                            createRequestUrl = extractUrlFromJavascript(href)
                            portletId = extractPortletId(createRequestUrl)
                            logger.info("Found create request button (method 2): portletId=$portletId")
                        }
                    }

                    // ポートレットIDが取得できた場合、直接JavaScriptを実行してモーダルを表示
                    if (portletId != null) {
                        try {
                            logger.info("Executing JavaScript to open modal dialog with portletId: $portletId")
                            val jsExecutor = driver as org.openqa.selenium.JavascriptExecutor

                            // ポートレットIDを使って直接aipo.common.showDialogを呼び出す
                            val showDialogScript = """
                                aipo.common.showDialog(
                                    'https://apps.uniss.co.jp/aipo/portal/js_peid/$portletId?template=WorkflowFormScreen&entityid=new',
                                    '$portletId',
                                    aipo.workflow.onLoadWorkflowDialog
                                );
                            """.trimIndent()

                            jsExecutor.executeScript(showDialogScript)
                            logger.info("JavaScript executed: aipo.common.showDialog for portletId=$portletId")

                            // モーダルダイアログ（id="modalDialog"）が表示されるまで待機
                            logger.info("Waiting for modal dialog to appear...")
                            try {
                                wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("modalDialog")))
                                logger.info("Modal dialog (id=modalDialog) is now visible")
                            } catch (e: Exception) {
                                logger.warn("Timeout waiting for modalDialog: ${e.message}")
                            }

                            // モーダルが表示されているか確認
                            val modalDialogElements = driver.findElements(By.id("modalDialog"))
                            if (modalDialogElements.isNotEmpty()) {
                                val modalDialog = modalDialogElements.first()
                                val displayStyle = modalDialog.getCssValue("display")
                                val opacity = modalDialog.getCssValue("opacity")
                                logger.info("Modal dialog found - display: $displayStyle, opacity: $opacity")

                                if (displayStyle == "block" && (opacity == "1" || opacity.toDoubleOrNull() == 1.0)) {
                                    logger.info("Modal dialog is fully visible and ready for interaction")
                                } else {
                                    logger.warn("Modal dialog may not be fully visible yet, waiting...")
                                    Thread.sleep(2000)
                                }
                            } else {
                                logger.warn("Modal dialog (id=modalDialog) not found")
                            }

                            // モーダル内のカテゴリプルダウンを待機
                            try {
                                wait.until(ExpectedConditions.presenceOfElementLocated(By.id("category_id")))
                                logger.info("Modal dialog loaded - category_id found")
                            } catch (e: Exception) {
                                logger.warn("Timeout waiting for category_id in modal: ${e.message}")
                            }

                            // ===== 1. カテゴリプルダウンで「勤務表」を選択 =====
                            val categoryElements = driver.findElements(By.id("category_id"))
                            if (categoryElements.isNotEmpty()) {
                                val categorySelect = categoryElements.first()
                                val select = org.openqa.selenium.support.ui.Select(categorySelect)

                                // 「勤務表」を選択（value="69"）
                                select.selectByValue("69")
                                logger.info("Selected '勤務表' from category dropdown")
                                timesheetSelected = true

                                // カテゴリ変更後のAjax更新を待機
                                Thread.sleep(2000)
                            } else {
                                logger.warn("Category dropdown not found in modal")
                            }

                            // ===== 2. 申請内容テキストエリアに入力 =====
                            try {
                                val noteElements = driver.findElements(By.id("workflow_Note"))
                                if (noteElements.isNotEmpty()) {
                                    val noteTextarea = noteElements.first()
                                    noteTextarea.clear()
                                    noteTextarea.sendKeys("勤務表を提出します。")
                                    logger.info("Entered text in workflow_Note textarea")
                                } else {
                                    logger.warn("workflow_Note textarea not found in modal")
                                }
                            } catch (e: Exception) {
                                logger.warn("Failed to enter text in textarea: ${e.message}")
                            }

                            // ===== 3. 申請経路を設定 =====
                            try {
                                // is_saved_route を FALSE に設定（ユーザー一覧から選択するモード）
                                val isSavedRouteElements = driver.findElements(By.id("is_saved_route"))
                                if (isSavedRouteElements.isNotEmpty()) {
                                    (driver as org.openqa.selenium.JavascriptExecutor).executeScript(
                                        "arguments[0].value = 'FALSE';", isSavedRouteElements.first()
                                    )
                                    logger.info("Set is_saved_route to FALSE")
                                }

                                // ユーザー一覧モードになっていることを確認
                                val routeSelectButton = driver.findElements(By.id("is_saved_route_button"))
                                if (routeSelectButton.isNotEmpty() && routeSelectButton.first().isDisplayed) {
                                    val buttonValue = routeSelectButton.first().getAttribute("value") ?: ""
                                    logger.info("Route select button value: '$buttonValue'")
                                    if (!buttonValue.contains("申請経路一覧から選択する")) {
                                        // 「ユーザー一覧から選択する」になっている場合はクリックして切り替え
                                        routeSelectButton.first().click()
                                        Thread.sleep(1500)
                                        logger.info("Clicked to switch to user list mode")
                                    }
                                }

                                // 右側のメンバーリストが読み込まれるまで待機
                                Thread.sleep(1000)

                                // 右側のメンバーリストから「角谷 亮洋」を選択
                                val memberFromElements = driver.findElements(By.id("tmp_member_from"))
                                if (memberFromElements.isNotEmpty()) {
                                    val memberFromSelect = memberFromElements.first()
                                    val memberFromDropdown = org.openqa.selenium.support.ui.Select(memberFromSelect)

                                    // 「角谷 亮洋」を選択（a_kakutani）
                                    try {
                                        memberFromDropdown.selectByValue("a_kakutani")
                                        logger.info("Selected 'a_kakutani' from member list")
                                        Thread.sleep(500)

                                        // 「追加」ボタンをクリック
                                        val addButtonElements = driver.findElements(By.id("button_member_add"))
                                        if (addButtonElements.isNotEmpty()) {
                                            addButtonElements.first().click()
                                            logger.info("Clicked add button to add member to route")
                                            Thread.sleep(1000)
                                        } else {
                                            logger.warn("Add button (button_member_add) not found")
                                        }
                                    } catch (e: Exception) {
                                        logger.warn("Failed to select member: ${e.message}")
                                    }
                                } else {
                                    logger.warn("Member from list (tmp_member_from) not found in modal")
                                }
                            } catch (e: Exception) {
                                logger.warn("Failed to set route: ${e.message}")
                            }

                            // ===== 4. ファイルをアップロード =====
                            // UNISS勤務表を自動生成してアップロード
                            var uploadFilePath: String? = timesheetFilePath
                            var tempFile: java.io.File? = null

                            // ファイルパスが指定されていない場合はUNISS勤務表を自動生成
                            if (uploadFilePath == null) {
                                try {
                                    // 前月の勤務表を生成
                                    val now = LocalDate.now()
                                    val targetMonth = now.minusMonths(1)
                                    val from = targetMonth.withDayOfMonth(1)
                                    val to = targetMonth.withDayOfMonth(targetMonth.lengthOfMonth())

                                    logger.info("Generating UNISS timesheet for $username: $from to $to")

                                    val xlsxBytes = reportService.generateUnissXlsxBytes(username, from, to)

                                    // 正しいファイル名で一時ファイルを作成
                                    val fileName = "${from.year}年${
                                        String.format(
                                            "%02d",
                                            from.monthValue
                                        )
                                    }月度UNISS勤務表(${username}).xlsx"
                                    val tempDir = java.io.File(System.getProperty("java.io.tmpdir"))
                                    tempFile = java.io.File(tempDir, fileName)
                                    tempFile.writeBytes(xlsxBytes)
                                    uploadFilePath = tempFile.absolutePath

                                    logger.info("Generated UNISS timesheet: $fileName, saved to: $uploadFilePath")
                                } catch (e: Exception) {
                                    logger.warn("Failed to generate UNISS timesheet: ${e.message}", e)
                                }
                            }

                            if (uploadFilePath != null) {
                                try {
                                    val timesheetFile = java.io.File(uploadFilePath)
                                    if (!timesheetFile.exists()) {
                                        logger.warn("Timesheet file not found: $uploadFilePath")
                                    } else {
                                        // ワークフローのポートレットIDに対応するファイルアップロード要素を探す
                                        // portletId = P-195e5838221-10004 の場合、
                                        // iframe: if_fileupload_P-195e5838221-10004
                                        // button: fileuploadButtonP-195e5838221-10004
                                        // attachments: attachments_P-195e5838221-10004

                                        var fileUploadButtons = mutableListOf<org.openqa.selenium.WebElement>()
                                        var inIframe = false
                                        var uploadPortletId: String? = portletId  // ワークフローのポートレットIDを使用

                                        logger.info("Looking for file upload elements for workflow portlet: $portletId")

                                        // まず、ワークフローのポートレットIDに対応するiframeを探す
                                        val targetIframeId = "if_fileupload_$portletId"
                                        val targetIframes = driver.findElements(By.id(targetIframeId))

                                        if (targetIframes.isNotEmpty()) {
                                            val iframe = targetIframes.first()
                                            logger.info("Found target iframe: $targetIframeId, switching to iframe...")
                                            driver.switchTo().frame(iframe)
                                            inIframe = true

                                            fileUploadButtons =
                                                driver.findElements(By.cssSelector("[id^='fileuploadButton']"))
                                                    .toMutableList()
                                            if (fileUploadButtons.isNotEmpty()) {
                                                val buttonId = fileUploadButtons.first().getAttribute("id") ?: ""
                                                logger.info("Found file upload button in target iframe: $buttonId")
                                            }
                                        } else {
                                            logger.info("Target iframe '$targetIframeId' not found, checking all iframes...")

                                            // 全てのiframeを確認
                                            val allIframes =
                                                driver.findElements(By.cssSelector("iframe[id^='if_fileupload_']"))
                                            logger.info("Found ${allIframes.size} file upload iframe(s)")

                                            for (iframe in allIframes) {
                                                val iframeId = iframe.getAttribute("id") ?: ""
                                                logger.info("Checking iframe: $iframeId")

                                                // ワークフローのポートレットIDを含むiframeを優先
                                                if (iframeId.contains(portletId ?: "")) {
                                                    logger.info("Found matching iframe for workflow: $iframeId")
                                                    driver.switchTo().frame(iframe)
                                                    inIframe = true

                                                    fileUploadButtons =
                                                        driver.findElements(By.cssSelector("[id^='fileuploadButton']"))
                                                            .toMutableList()
                                                    if (fileUploadButtons.isNotEmpty()) {
                                                        val buttonId =
                                                            fileUploadButtons.first().getAttribute("id") ?: ""
                                                        logger.info("Found file upload button: $buttonId")
                                                    }
                                                    break
                                                }
                                            }

                                            // マッチするiframeが見つからない場合は最初のiframeを使用（フォールバック）
                                            if (!inIframe && allIframes.isNotEmpty()) {
                                                val iframe = allIframes.first()
                                                val iframeId = iframe.getAttribute("id") ?: ""
                                                logger.warn("No matching iframe found, using first iframe: $iframeId")
                                                driver.switchTo().frame(iframe)
                                                inIframe = true

                                                fileUploadButtons =
                                                    driver.findElements(By.cssSelector("[id^='fileuploadButton']"))
                                                        .toMutableList()
                                                if (fileUploadButtons.isNotEmpty()) {
                                                    val buttonId = fileUploadButtons.first().getAttribute("id") ?: ""
                                                    uploadPortletId = buttonId.removePrefix("fileuploadButton")
                                                        .removePrefix("global-")
                                                    logger.info("Found file upload button in fallback iframe: $buttonId, portletId: $uploadPortletId")
                                                }
                                            }
                                        }

                                        if (fileUploadButtons.isNotEmpty() && uploadPortletId != null) {
                                            val fileUploadButton = fileUploadButtons.first()
                                            logger.info("Upload portlet ID: $uploadPortletId")

                                            // ボタン内のinput[type=file]を探す（id="attachment"も含む）
                                            var fileInputs =
                                                fileUploadButton.findElements(By.cssSelector("input[type='file']"))
                                            if (fileInputs.isEmpty()) {
                                                // id="attachment"で直接検索
                                                fileInputs = driver.findElements(By.id("attachment"))
                                            }

                                            if (fileInputs.isNotEmpty()) {
                                                val fileInput = fileInputs.first()

                                                // ファイルパスを設定
                                                fileInput.sendKeys(timesheetFile.absolutePath)
                                                logger.info("Set file to input: ${timesheetFile.absolutePath}")

                                                Thread.sleep(500)

                                                // iframe内でonchangeイベントをトリガー（iframe内で実行）
                                                val triggerScript = """
                                                    (function() {
                                                        var fileInput = document.querySelector("input[type='file'], #attachment");
                                                        if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
                                                            return 'NO_FILE';
                                                        }
                                                        
                                                        // onchange属性を実行
                                                        if (fileInput.onchange) {
                                                            fileInput.onchange();
                                                            return 'ONCHANGE_CALLED:' + fileInput.files[0].name;
                                                        }
                                                        
                                                        // changeイベントをディスパッチ
                                                        var event = new Event('change', { bubbles: true });
                                                        fileInput.dispatchEvent(event);
                                                        return 'EVENT_DISPATCHED:' + fileInput.files[0].name;
                                                    })();
                                                """.trimIndent()

                                                // iframe内でJavaScriptを実行
                                                val triggerResult = jsExecutor.executeScript(triggerScript)
                                                logger.info("Trigger result (in iframe): $triggerResult")

                                                // メインコンテキストに戻る
                                                if (inIframe) {
                                                    driver.switchTo().defaultContent()
                                                    logger.info("Switched back to main context after triggering change event")
                                                    inIframe = false
                                                }

                                                // アップロード完了を待機
                                                Thread.sleep(5000)

                                                // アップロード完了を確認（メインコンテキストで添付ファイルリストを確認）
                                                val attachmentList =
                                                    driver.findElements(By.cssSelector("[id^='attachments_'] li"))
                                                if (attachmentList.isNotEmpty()) {
                                                    uploadedFileName =
                                                        attachmentList.first().getAttribute("data-filename")
                                                            ?: attachmentList.first().text.replace("削除", "")
                                                                .replace("\u200B", "").trim()
                                                    fileUploaded = true
                                                    logger.info("File upload confirmed via attachment list: $uploadedFileName")
                                                } else {
                                                    logger.warn("Attachment list is empty after upload attempt")

                                                    // 代替確認: folderName hidden inputが設定されているか確認
                                                    val folderNameInputs =
                                                        driver.findElements(By.cssSelector("[id^='folderName_']"))
                                                    if (folderNameInputs.isNotEmpty()) {
                                                        val folderValue =
                                                            folderNameInputs.first().getAttribute("value") ?: ""
                                                        if (folderValue.isNotBlank()) {
                                                            fileUploaded = true
                                                            // ファイル名は生成時の名前を使用
                                                            uploadedFileName = timesheetFile.name
                                                            logger.info("File upload confirmed via folderName input: $folderValue, using filename: $uploadedFileName")
                                                        }
                                                    }
                                                }
                                            } else {
                                                logger.warn("File input not found in button or by id='attachment'")
                                            }
                                        } else {
                                            logger.warn("File upload button not found in any context")
                                        }

                                        // 必ずメインコンテキストに戻る
                                        if (inIframe) {
                                            driver.switchTo().defaultContent()
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.warn("Failed to upload: ${e.message}", e)
                                    // エラー時もメインコンテキストに戻る
                                    try {
                                        driver.switchTo().defaultContent()
                                    } catch (ignored: Exception) {
                                    }
                                } finally {
                                    tempFile?.delete()
                                }
                            }

                            // ===== 5. フォームプレビュー情報を収集 =====
                            formPreview = collectFormPreview(driver)

                            // アップロードで取得したファイル名がある場合は反映
                            if (!uploadedFileName.isNullOrBlank() && formPreview?.attachedFileName.isNullOrBlank()) {
                                formPreview = formPreview?.copy(attachedFileName = uploadedFileName)
                                logger.info("Form preview - attached file updated from upload: $uploadedFileName")
                            }

                            logger.info("Form preview collected: $formPreview")

                            // ===== 6. 自動送信（autoSubmitがtrueかつフォームが準備完了の場合） =====
                            if (autoSubmit && formPreview?.isReady == true && formPreview.submitButtonId != null) {
                                try {
                                    logger.info("Auto submit enabled. Clicking submit button: ${formPreview.submitButtonId}")
                                    val submitButton = driver.findElement(By.id(formPreview.submitButtonId))
                                    if (submitButton != null && submitButton.isDisplayed) {
                                        submitButton.click()
                                        logger.info("Auto submit: Clicked submit button")
                                        Thread.sleep(3000)
                                        autoSubmitted = true
                                    }
                                } catch (e: Exception) {
                                    logger.warn("Auto submit failed: ${e.message}", e)
                                }
                            }

                        } catch (e: Exception) {
                            logger.warn("Failed to process modal dialog: ${e.message}", e)
                            // エラーがあってもプレビュー情報を収集を試みる
                            formPreview = collectFormPreview(driver)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to find workflow link or create request button: ${e.message}", e)
            }

            // セッションを保存
            val sessionId = driver.sessionId?.toString() ?: "unknown"
            userSessions[username] = driver

            logger.info("Aipo login successful for user: $username, sessionId: $sessionId, workflowUrl: $workflowUrl, createRequestUrl: $createRequestUrl, timesheetSelected: $timesheetSelected, fileUploaded: $fileUploaded, formReady: ${formPreview?.isReady}, autoSubmitted: $autoSubmitted")

            val message = when {
                autoSubmitted -> "Aipoへのログインに成功しました。申請が自動送信されました。"
                formPreview?.isReady == true -> "Aipoへのログインに成功しました。申請準備が完了しました。"
                fileUploaded -> "Aipoへのログインに成功しました。勤務表ファイルがアップロードされました。"
                timesheetSelected -> "Aipoへのログインに成功しました。勤務表が選択されました。"
                createRequestUrl != null -> "Aipoへのログインに成功しました。依頼作成ボタンが見つかりました。"
                workflowUrl != null -> "Aipoへのログインに成功しました。ワークフローが見つかりました。"
                else -> "Aipoへのログインに成功しました"
            }

            return AipoLoginResult(
                success = true,
                message = message,
                sessionId = sessionId,
                workflowUrl = workflowUrl,
                createRequestUrl = createRequestUrl,
                timesheetSelected = timesheetSelected,
                fileUploaded = fileUploaded,
                formPreview = formPreview,
                autoSubmitted = autoSubmitted
            )

        } catch (e: Exception) {
            logger.error("Aipo login failed for user: $username", e)
            driver?.quit()
            return AipoLoginResult(
                success = false,
                message = "ログインに失敗しました: ${e.message}"
            )
        }
    }

    /**
     * ユーザーのAipoセッションをログアウト
     */
    fun logout(username: String): Boolean {
        return try {
            userSessions.remove(username)?.let { driver ->
                try {
                    // AipoのログアウトURLにアクセス
                    driver.get(AIPO_LOGOUT_URL)
                    logger.info("Aipo logout URL accessed for user: $username")
                } catch (e: Exception) {
                    logger.warn("Failed to access Aipo logout URL for user: $username", e)
                } finally {
                    driver.quit()
                }
                logger.info("Aipo session closed for user: $username")
                true
            } ?: false
        } catch (e: Exception) {
            logger.error("Failed to logout Aipo session for user: $username", e)
            false
        }
    }

    /**
     * ユーザーがAipoにログイン中かどうか
     */
    fun isLoggedIn(username: String): Boolean {
        return userSessions.containsKey(username) && try {
            userSessions[username]?.currentUrl != null
        } catch (e: Exception) {
            userSessions.remove(username)
            false
        }
    }

    /**
     * Aipoで申請を実行
     * @param username ユーザー名（セッション管理用）
     * @param submitButtonId 申請ボタンのID
     * @return Pair<Boolean, String> (成功/失敗, メッセージ)
     */
    fun submitRequest(username: String, submitButtonId: String): Pair<Boolean, String> {
        val driver = userSessions[username]
        if (driver == null) {
            logger.warn("No session found for user: $username")
            return Pair(false, "セッションが見つかりません。再度ログインしてください。")
        }

        try {
            logger.info("Submitting request for user: $username with button ID: $submitButtonId")

            // 申請ボタンを探してクリック
            val submitButton = driver.findElement(By.id(submitButtonId))
            if (submitButton == null || !submitButton.isDisplayed) {
                logger.warn("Submit button not found or not visible: $submitButtonId")
                return Pair(false, "申請ボタンが見つかりません。Aipo画面を確認してください。")
            }

            // ボタンをクリック
            submitButton.click()
            logger.info("Clicked submit button: $submitButtonId")

            // 申請完了を待機
            Thread.sleep(3000)

            // 成功メッセージがあるか確認
            val successElements = driver.findElements(By.cssSelector(".success, .alert-success, .message-success"))
            if (successElements.isNotEmpty() && successElements.any { it.isDisplayed }) {
                val successMessage = successElements.first { it.isDisplayed }.text
                logger.info("Submit successful: $successMessage")
                return Pair(true, "申請が完了しました: $successMessage")
            }

            // エラーメッセージがあるか確認
            val errorElements = driver.findElements(By.cssSelector(".error, .alert-danger, .message-error"))
            if (errorElements.isNotEmpty() && errorElements.any { it.isDisplayed }) {
                val errorMessage = errorElements.first { it.isDisplayed }.text
                logger.warn("Submit error: $errorMessage")
                return Pair(false, "申請エラー: $errorMessage")
            }

            // 特にメッセージがない場合は成功とみなす
            logger.info("Submit completed (no message found)")
            return Pair(true, "申請が完了しました")

        } catch (e: Exception) {
            logger.error("Failed to submit request for user: $username", e)
            return Pair(false, "申請に失敗しました: ${e.message}")
        }
    }

    /**
     * フォームの入力状況をチェックしてプレビュー情報を収集
     */
    private fun collectFormPreview(driver: WebDriver): AipoFormPreview {
        var category: String? = null
        var note: String? = null
        val routeMembers = mutableListOf<String>()
        var attachedFileName: String? = null
        var submitButtonId: String? = null
        var fileUploadButtonId: String? = null
        var fileUploadButtonExists = false
        var fileInputExists = false

        try {
            // カテゴリの選択状態を取得
            try {
                val categorySelect = driver.findElement(By.id("category_id"))
                val select = org.openqa.selenium.support.ui.Select(categorySelect)
                category = select.firstSelectedOption?.text
                logger.info("Form preview - category: $category")
            } catch (e: Exception) {
                logger.warn("Failed to get category: ${e.message}")
            }

            // テキストエリアの内容を取得
            try {
                val noteTextarea = driver.findElement(By.id("workflow_Note"))
                note = noteTextarea.getAttribute("value")
                logger.info("Form preview - note: $note")
            } catch (e: Exception) {
                logger.warn("Failed to get note: ${e.message}")
            }

            // 申請経路順のメンバーを取得
            try {
                val positionsSelect = driver.findElement(By.id("positions"))
                val options = positionsSelect.findElements(By.tagName("option"))
                for (option in options) {
                    val memberName = option.text
                    if (memberName.isNotBlank()) {
                        routeMembers.add(memberName)
                    }
                }
                logger.info("Form preview - route members: $routeMembers")
            } catch (e: Exception) {
                logger.warn("Failed to get route members: ${e.message}")
            }

            // 添付ファイル名を取得
            try {
                // アップロード済みファイルのリストを探す（動的にIDを検索）
                // 例: <ul id="attachments_P-195e5838221-10004" class="attachments">
                val attachmentLists = driver.findElements(By.cssSelector("[id^='attachments_']"))
                logger.info("Form preview - found ${attachmentLists.size} attachment list(s)")

                for (list in attachmentLists) {
                    val listId = list.getAttribute("id") ?: ""
                    val listItems = list.findElements(By.tagName("li"))
                    logger.info("Form preview - attachment list '$listId' has ${listItems.size} item(s)")
                }

                val attachmentList = driver.findElements(By.cssSelector("[id^='attachments_'] li, .attachments li"))
                logger.info("Form preview - total attachment items found: ${attachmentList.size}")

                if (attachmentList.isNotEmpty()) {
                    val firstAttachment = attachmentList.first()
                    val dataFileId = firstAttachment.getAttribute("data-fileid") ?: ""
                    val dataFilename = firstAttachment.getAttribute("data-filename") ?: ""
                    val liText = firstAttachment.text

                    logger.info("Form preview - first attachment: data-fileid='$dataFileId', data-filename='$dataFilename', text='$liText'")

                    // data-filename属性から正確なファイル名を取得（推奨）
                    if (dataFilename.isNotBlank()) {
                        attachedFileName = dataFilename
                    } else {
                        // data-filenameがない場合はspan内のテキストを取得（削除ボタンのテキストを除外）
                        val spanElements = firstAttachment.findElements(By.tagName("span"))
                        if (spanElements.isNotEmpty()) {
                            // 最初のspanがファイル名、deletebutton classを持つspanは除外
                            val fileNameSpan = spanElements.find { span ->
                                !span.getAttribute("class").orEmpty().contains("deletebutton")
                            }
                            attachedFileName = fileNameSpan?.text?.replace("\u200B", "")?.trim() // ゼロ幅スペース除去
                        }
                        // spanがない場合はliのテキストから「削除」を除いた部分を取得
                        if (attachedFileName.isNullOrBlank()) {
                            attachedFileName = firstAttachment.text
                                .replace("削除", "")
                                .replace("\u200B", "") // ゼロ幅スペース除去
                                .trim()
                        }
                    }
                    logger.info("Form preview - attached file: $attachedFileName")
                }

                // folderName hidden inputの存在確認（ワークフロー用を優先）
                val allFolderNameInputs = driver.findElements(By.cssSelector("[id^='folderName_']"))
                logger.info("Form preview - found ${allFolderNameInputs.size} folderName input(s)")

                for (input in allFolderNameInputs) {
                    val folderId = input.getAttribute("id") ?: ""
                    val folderValue = input.getAttribute("value") ?: ""
                    logger.info("Form preview - folderName: id='$folderId', value='$folderValue'")
                }
            } catch (e: Exception) {
                logger.warn("Failed to get attached file: ${e.message}")
            }

            // 申請ボタンのIDを動的に取得
            try {
                // al_submit_で始まるIDを持つボタンを探す
                val submitButtons = driver.findElements(By.cssSelector("input[id^='al_submit_']"))
                if (submitButtons.isNotEmpty()) {
                    submitButtonId = submitButtons.first().getAttribute("id")
                    logger.info("Form preview - submit button ID: $submitButtonId")
                }
            } catch (e: Exception) {
                logger.warn("Failed to get submit button ID: ${e.message}")
            }

            // ファイル追加ボタンの存在確認（メインコンテキスト）
            try {
                val fileUploadButtons = driver.findElements(By.cssSelector("[id^='fileuploadButton']"))
                if (fileUploadButtons.isNotEmpty()) {
                    val fileUploadButton = fileUploadButtons.first()
                    fileUploadButtonId = fileUploadButton.getAttribute("id")
                    fileUploadButtonExists = fileUploadButton.isDisplayed
                    logger.info("Form preview - file upload button ID: $fileUploadButtonId, exists: $fileUploadButtonExists")

                    // ボタン内のinput[type=file]を探す
                    val fileInputs = fileUploadButton.findElements(By.cssSelector("input[type='file']"))
                    if (fileInputs.isNotEmpty()) {
                        fileInputExists = true
                        logger.info("Form preview - file input exists: true")
                    } else {
                        logger.warn("Form preview - file input NOT found inside upload button")
                    }
                } else {
                    logger.warn("Form preview - file upload button NOT found in main context")
                }

                // id="attachment" の存在を直接確認（メインコンテキスト）
                val attachmentInputs = driver.findElements(By.id("attachment"))
                if (attachmentInputs.isNotEmpty() && attachmentInputs.first().isDisplayed) {
                    fileInputExists = true
                    logger.info("Form preview - attachment input (id='attachment') found and displayed in main context")
                } else if (attachmentInputs.isNotEmpty()) {
                    fileInputExists = true
                    logger.info("Form preview - attachment input (id='attachment') found (hidden but exists) in main context")
                }

                // iframe内を確認（ファイルアップロード用iframe）
                if (!fileUploadButtonExists || !fileInputExists) {
                    val fileUploadIframes = driver.findElements(By.cssSelector("iframe[id^='if_fileupload_']"))
                    if (fileUploadIframes.isNotEmpty()) {
                        val iframe = fileUploadIframes.first()
                        val iframeId = iframe.getAttribute("id") ?: ""
                        logger.info("Form preview - Found file upload iframe: $iframeId, switching to iframe...")

                        try {
                            driver.switchTo().frame(iframe)

                            // iframe内でファイル追加ボタンを探す
                            val iframeFileUploadButtons =
                                driver.findElements(By.cssSelector("[id^='fileuploadButton']"))
                            if (iframeFileUploadButtons.isNotEmpty()) {
                                val btn = iframeFileUploadButtons.first()
                                fileUploadButtonId = btn.getAttribute("id")
                                fileUploadButtonExists = true
                                logger.info("Form preview - file upload button found in iframe: $fileUploadButtonId")
                            }

                            // iframe内でid="attachment"を探す
                            val iframeAttachmentInputs = driver.findElements(By.id("attachment"))
                            if (iframeAttachmentInputs.isNotEmpty()) {
                                fileInputExists = true
                                logger.info("Form preview - attachment input (id='attachment') found in iframe")
                            }

                            // iframe内でinput[type='file']を探す
                            val iframeFileInputs = driver.findElements(By.cssSelector("input[type='file']"))
                            if (iframeFileInputs.isNotEmpty()) {
                                fileInputExists = true
                                logger.info("Form preview - file input found in iframe")
                            }

                        } finally {
                            // メインコンテキストに戻る
                            driver.switchTo().defaultContent()
                            logger.info("Form preview - Switched back to main context")
                        }
                    } else {
                        logger.warn("Form preview - No file upload iframe found")
                    }
                }

                if (!fileUploadButtonExists && !fileInputExists) {
                    logger.warn("Form preview - attachment input (id='attachment') NOT found in any context")
                }
            } catch (e: Exception) {
                logger.warn("Failed to check file upload button: ${e.message}")
                // エラー時はメインコンテキストに戻ることを保証
                try {
                    driver.switchTo().defaultContent()
                } catch (ignored: Exception) {
                }
            }

        } catch (e: Exception) {
            logger.warn("Error collecting form preview: ${e.message}")
        }

        // 全ての必須項目が入力されているかチェック
        val isReady = !category.isNullOrBlank() &&
                category != "未分類" &&
                !note.isNullOrBlank() &&
                routeMembers.isNotEmpty() &&
                submitButtonId != null

        return AipoFormPreview(
            category = category,
            note = note,
            routeMembers = routeMembers,
            attachedFileName = attachedFileName,
            submitButtonId = submitButtonId,
            fileUploadButtonId = fileUploadButtonId,
            fileUploadButtonExists = fileUploadButtonExists,
            fileInputExists = fileInputExists,
            isReady = isReady
        )
    }

    /**
     * 全セッションをクリーンアップ
     */
    fun cleanupAllSessions() {
        userSessions.forEach { (username, driver) ->
            try {
                driver.quit()
                logger.info("Cleaned up Aipo session for user: $username")
            } catch (e: Exception) {
                logger.warn("Failed to cleanup session for user: $username", e)
            }
        }
        userSessions.clear()
    }
}


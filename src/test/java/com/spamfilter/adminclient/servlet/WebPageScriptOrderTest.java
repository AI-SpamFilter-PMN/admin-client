package com.spamfilter.adminclient.servlet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared runtime script (api/showToast/etc.) must appear before any
 * page-specific inline script in the rendered HTML - browsers execute
 * inline &lt;script&gt; tags synchronously in document order, and several
 * pages (Logs/Calls/Messages/Whitelist) call load() immediately at the
 * bottom of their own script rather than deferring to DOMContentLoaded.
 * If the shared script were ever moved back below the page body, that
 * first load() call throws "api is not defined", which is swallowed and
 * leaves totalPages stuck at its initial value of 1 - silently breaking
 * the Next button until some other successful load() call runs.
 */
class WebPageScriptOrderTest {

    @Test
    void sharedRuntimeScriptPrecedesPageBodyScript() {
        String pageScript = "<script>(function(){ load(); })();</script>";
        String html = WebPage.shell("Test", "subtitle", "/test", "admin@example.com", pageScript);

        int runtimeScriptIndex = html.indexOf("function api(");
        int pageScriptIndex = html.indexOf(pageScript);

        assertTrue(runtimeScriptIndex >= 0, "shared runtime script (defining api()) should be present");
        assertTrue(pageScriptIndex >= 0, "page body should be present in the rendered shell");
        assertTrue(runtimeScriptIndex < pageScriptIndex,
                "shared runtime script must be parsed/executed before the page's own inline script");
    }
}

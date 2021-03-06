package com.bitbreeds.webrtc.signaling;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.camel.CamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/*
 * Copyright (c) 27/06/16, Jonas Waage
 */

public class BrowserChromeLossyIntegrationTest {

    private WebDriver driver;
    private CamelContext ctx;


    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Before
    public void setup() {
        driver = CommonTestMethods.chromeDriver();
    }

    @After
    public void tearDown() {
        driver.close();
        driver.quit();
        ctx.stop();
    }

    @Test
    public void testAllMessagesFinished() throws Exception {

        ctx = SimpleSignaling.camelContextLossy(5,5,SimpleSignaling::setupPeerConnectionDuplicateCheck);
        ctx.start();

        File fl = new File(".././web/transfer.html");

        String url = "file://" + fl.getAbsolutePath();
        System.out.println(url);
        driver.get(url);

        (new WebDriverWait(driver, 60)).until(
                (ExpectedCondition<Boolean>) d -> {
                    assert d != null;
                    return d.findElement(By.id("all-received")).getText().equalsIgnoreCase("ALL RECEIVED");
                }
        );

        ctx.stop();
    }

}

package com.bitbreeds.webrtc.signaling;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.camel.CamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;

/*
 * Copyright (c) 27/06/16, Jonas Waage
 */

public class BrowserFirefoxIntegrationTest {

    private WebDriver driver;
    private CamelContext ctx;


    @Before
    public void setup() {
        driver = CommonTestMethods.firefoxDriver();
    }

    @After
    public void tearDown() {
        driver.quit();
        ctx.stop();
    }

    @Test
    public void testOpen() throws Exception {

        ctx = SimpleSignaling.initContext(SimpleSignaling::setupPeerConnectionDuplicateCheckNoThrow);
        ctx.start();

        File fl = new File(".././web/index.html");

        String url = "file://" + fl.getAbsolutePath();
        System.out.println(url);
        driver.get(url);

        (new WebDriverWait(driver, 30)).until(
                (ExpectedCondition<Boolean>) d -> {
                    assert d != null;
                    return d.findElement(By.id("status")).getText().equalsIgnoreCase("ONMESSAGE");
                }
        );

        ctx.stop();
    }


    @Test
    public void testAllMessages() throws Exception {

        ctx = SimpleSignaling.initContext(SimpleSignaling::setupPeerConnectionDuplicateCheck);
        ctx.start();

        File fl = new File(".././web/transfer.html");

        String url = "file://" + fl.getAbsolutePath();
        System.out.println(url);
        driver.get(url);

        (new WebDriverWait(driver, 30)).until(
                (ExpectedCondition<Boolean>) d -> {
                    assert d != null;
                    return d.findElement(By.id("all-received")).getText().equalsIgnoreCase("ALL RECEIVED");
                }
        );

        ctx.stop();
    }



}

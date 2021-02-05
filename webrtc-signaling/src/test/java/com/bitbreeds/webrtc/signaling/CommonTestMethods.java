package com.bitbreeds.webrtc.signaling;/*
 *
 * Copyright (c) 02/01/2020, Jonas Waage
 */

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

public class CommonTestMethods {

    public static WebDriver firefoxDriver(boolean headless) {
        TestKeystoreParams.initialize();
        FirefoxOptions options = new FirefoxOptions();
        options.setHeadless(headless);
        WebDriverManager.firefoxdriver().setup();
        return new FirefoxDriver(options);
    }

    public static WebDriver firefoxDriver() {
        return firefoxDriver(true);
    }


    public static WebDriver chromeDriver() {
        return chromeDriver(true);
    }

    public static WebDriver chromeDriver(boolean headless) {
        TestKeystoreParams.initialize();
        ChromeOptions options = new ChromeOptions();
        options.setHeadless(headless);
        WebDriverManager.chromedriver().setup();
        return new ChromeDriver(options);
    }

}

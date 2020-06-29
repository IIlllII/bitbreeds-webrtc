package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.test.categories.LongRunning;
import org.apache.camel.CamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/*
 * Copyright (c) 27/06/16, Jonas Waage
 */

@Category(LongRunning.class)
public class BrowserFirefoxLossyJitterTotalUDPTest {

    private WebDriver driver;
    private CamelContext ctx;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Before
    public void setup() {
        driver = CommonTestMethods.firefoxDriver(false);
    }

    @After
    public void tearDown() {
        driver.quit();
        ctx.stop();
    }

    @Test
    public void testLatencyWithDropJitterAndDelay() throws Exception {

        ArrayList<SimpleSignaling.ReceivedRecord> rec = new ArrayList<>();
        ctx = SimpleSignaling.camelContextDelayJitterLoss(5, 5, 25,15, c -> SimpleSignaling.gameServerEquivalent(c,rec));
        ctx.start();

        File fl = new File(".././web/transfer-loss-delay-jitter.html");

        String url = "file://" + fl.getAbsolutePath();
        System.out.println(url);
        driver.get(url);

        (new WebDriverWait(driver, 30)).until(
                (ExpectedCondition<Boolean>) d -> {
                    assert d != null;
                    return d.findElement(By.id("all-received")).getText().equalsIgnoreCase("NO DELAY OVER 200MS");
                }
        );

        ctx.stop();

        long max = 0;
        int record = 0;
        for(int i = 1; i < rec.size(); i++) {
            long diff = rec.get(i).time - rec.get(i-1).time;
            if(diff > max) {
                record = i;
            }
            max = Math.max(diff,max);
        }

        logger.info("Records received: {}",rec.size());
        logger.info("MaxDelay:{}",max);
        if(max > 200) {
            logger.error("Records {} {}",rec.get(record-1),rec.get(record));
        }

        assertTrue(max < 200);
        assertEquals(rec.stream().distinct().count(),rec.size());
    }

}

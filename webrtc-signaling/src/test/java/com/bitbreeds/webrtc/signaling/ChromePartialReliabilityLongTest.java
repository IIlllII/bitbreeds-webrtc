package com.bitbreeds.webrtc.signaling;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.camel.CamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertTrue;

/*
 * Copyright (c) 27/06/16, Jonas Waage
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

@Ignore
public class ChromePartialReliabilityLongTest {

    private WebDriver driver;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Before
    public void setup() {
        driver = CommonTestMethods.chromeDriver();
    }

    @After
    public void tearDown() {
        driver.close();
        driver.quit();
    }

    @Test
    public void testMessagesDroppedDueToPartialReliabilityLong() throws Exception {

        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        AtomicInteger num = new AtomicInteger( 0);

        AtomicBoolean first = new AtomicBoolean(true);
        AtomicLong time = new AtomicLong(0);
        AtomicLong max = new AtomicLong(0);

        try {
            CamelContext ctx = SimpleSignaling.camelContextLossy(5, 5,
                    peerServer -> {

                        peerServer.onConnection = connection  -> {

                            connection.onDataChannel  = dataChannel -> {

                                dataChannel.onOpen = openEvent -> {
                                    service.scheduleAtFixedRate(
                                            () -> {
                                                String data = "My message number "+num.getAndIncrement();
                                                logger.warn("Sending: "+ data);
                                                dataChannel.send(data);
                                            },25,25, TimeUnit.MILLISECONDS
                                    );
                                };

                                dataChannel.onMessage = messageEvent -> {
                                    logger.info("Received: "+new String(messageEvent.getData()));
                                    if(first.get()) {
                                        first.set(false);
                                        long current = System.currentTimeMillis();
                                        time.set(current);
                                        logger.warn("First message, time "+current);
                                    }
                                    else {
                                        long newTime = System.currentTimeMillis();
                                        long diff = newTime - time.get();
                                        time.set(newTime);
                                        max.set(Math.max(max.get(),diff));
                                        logger.warn("Diff is " + diff + " Max is: "+ max.get());
                                    }
                                };

                            };

                        };

                    });

            ctx.start();

            File fl = new File(".././web/transfer-loss-partial-reliability-long.html");

            String url = "file://" + fl.getAbsolutePath();
            System.out.println(url);
            driver.get(url);

            (new WebDriverWait(driver, 60)).until(
                    (ExpectedCondition<Boolean>) d -> {
                        assert d != null;
                        return d.findElement(By.id("all-received")).getText().contains("SUCCESS");
                    }
            );

            service.shutdown();
            ctx.stop();

            assertTrue("Max time without message is "+max.get(),max.get() < 200);
        } finally {
        }
    }

}
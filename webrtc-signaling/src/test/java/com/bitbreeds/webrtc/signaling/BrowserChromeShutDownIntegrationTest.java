package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.peerconnection.ConnectionImplementation;
import com.bitbreeds.webrtc.peerconnection.SimplePeerServer;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.camel.CamelContext;
import org.apache.camel.component.websocket.WebsocketComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.jndi.JndiContext;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bitbreeds.webrtc.signaling.SimpleSignaling.sslParameters;

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

public class BrowserChromeShutDownIntegrationTest {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private WebDriver driver;

    @Before
    public void setupDriver() {
        driver = CommonTestMethods.chromeDriver();
    }

    @After
    public void tearDown() {
        driver.close();
        driver.quit();
    }


    @Test
    public void testShutdown() throws Exception {
        JndiRegistry reg = new JndiRegistry(new JndiContext());
        reg.bind("sslContextParameters",sslParameters());

        SimplePeerServer peerConnectionServer = new SimplePeerServer(SimpleSignaling.keyStoreInfo);

        peerConnectionServer.onConnection = (connection) -> {
            connection.onDataChannel = (dataChannel) -> {
                dataChannel.onOpen = (ev) -> {
                    logger.info("Running onOpen");
                    dataChannel.send("OPEN!");
                };
                dataChannel.onMessage = (ev) -> {
                    String in = new String(ev.getData());
                    logger.info("Running onMessage: " + in);
                    dataChannel.send("echo-" + in);
                };
                dataChannel.onClose = (ev) -> {
                    logger.info("Received close: {}", ev);
                };
                dataChannel.onError = (ev) -> {
                    logger.info("Received error: {}", ev.getError());
                };
            };
        };

        CamelContext ctx = new DefaultCamelContext(reg);
        WebsocketComponent component = (WebsocketComponent)ctx.getComponent("websocket");
        component.setMinThreads(1);
        component.setMaxThreads(15);

        ctx.addRoutes(new SimpleSignaling.WebsocketRouteNoSSL(peerConnectionServer));
        ctx.addRoutes(new SimpleSignaling.OutRoute());
        ctx.addRoutes(new SimpleSignaling.DelayRoute());
        ctx.setUseMDCLogging(true);
        ctx.setTracing(true);

        ctx.start();

        new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            peerConnectionServer.getConnections().values().forEach(
                    ConnectionImplementation::close
            );
        }).start();


        File fl = new File(".././web/shutdown.html");

        String url = "file://" + fl.getAbsolutePath();
        System.out.println(url);
        driver.get(url);

        (new WebDriverWait(driver, 10)).until(
                (ExpectedCondition<Boolean>) d -> {
                    assert d != null;
                    return d.findElement(By.id("status"))
                            .getText()
                            .equalsIgnoreCase("SHUTDOWN");
                }
        );

        ctx.stop();
    }



    @Test
    public void testBrowserInitiatedShutdown() throws Exception {
        JndiRegistry reg = new JndiRegistry(new JndiContext());
        reg.bind("sslContextParameters",sslParameters());

        SimplePeerServer peerConnectionServer = new SimplePeerServer(SimpleSignaling.keyStoreInfo);

        AtomicBoolean wasShutDown = new AtomicBoolean(false);

        peerConnectionServer.onConnection = (connection) -> {
            connection.onDataChannel = (dataChannel) -> {
                dataChannel.onOpen = (ev) -> {
                    logger.info("Running onOpen");
                    dataChannel.send("OPEN!");
                };
                dataChannel.onMessage = (ev) -> {
                    String in = new String(ev.getData());
                    logger.info("Running onMessage: " + in);
                    dataChannel.send("echo-" + in);
                };
                dataChannel.onClose = (ev) -> {
                    wasShutDown.set(true);
                    logger.info("DC close: {}", ev);
                };
                dataChannel.onError = (ev) -> {
                    logger.info("Received error: {}", ev.getError());
                };
            };
        };

        CamelContext ctx = new DefaultCamelContext(reg);
        WebsocketComponent component = (WebsocketComponent)ctx.getComponent("websocket");
        component.setMinThreads(1);
        component.setMaxThreads(15);

        ctx.addRoutes(new SimpleSignaling.WebsocketRouteNoSSL(peerConnectionServer));
        ctx.addRoutes(new SimpleSignaling.OutRoute());
        ctx.addRoutes(new SimpleSignaling.DelayRoute());
        ctx.setUseMDCLogging(true);
        ctx.setTracing(true);

        ctx.start();

        File fl = new File(".././web/shutdownBrowserInit.html");

        String url = "file://" + fl.getAbsolutePath();
        System.out.println(url);
        driver.get(url);

        (new WebDriverWait(driver, 120)).until(
                (ExpectedCondition<Boolean>) d -> {
                    assert d != null;
                    return wasShutDown.get();
                }
        );

        ctx.stop();
    }
}


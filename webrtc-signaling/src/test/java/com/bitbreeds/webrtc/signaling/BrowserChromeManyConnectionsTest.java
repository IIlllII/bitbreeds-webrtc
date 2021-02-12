package com.bitbreeds.webrtc.signaling;

import com.bitbreeds.webrtc.peerconnection.SimplePeerServer;
import com.bitbreeds.webrtc.test.categories.LongRunning;
import org.apache.camel.CamelContext;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.FastScatterPlot;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/*
 * Copyright (c) 27/06/16, Jonas Waage
 */

@Category(LongRunning.class)
public class BrowserChromeManyConnectionsTest {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private List<WebDriver> drivers;
    private CamelContext ctx;
    private File chartFile = new File("./target/charts/");

    @Before
    public void setup() {

        if(!chartFile.exists()) {
            if(!chartFile.mkdir()) {
                throw new IllegalStateException("Could not create charts folder");
            }
        }

        drivers = IntStream.range(0,20)
                .mapToObj(i->CommonTestMethods.chromeDriver(true))
                .collect(Collectors.toList());
    }

    @After
    public void tearDown() {
        drivers.forEach(i-> {
            i.close();
            i.quit();
        });

        ctx.stop();
    }

    @Test
    public void test20Clients() throws Exception {

        ConcurrentHashMap<String,List<SimpleSignaling.ReceivedRecord>> rec = new ConcurrentHashMap<>();
        AtomicReference<SimplePeerServer> serv = new AtomicReference<>();
        ctx = SimpleSignaling.initContext(c -> {
            serv.set(c);
            SimpleSignaling.gameServerEquivalent(c,rec);
        });
        ctx.start();

        File fl = new File(".././web/send-every-25ms-keep-track.html");

        String url = "file://" + fl.getAbsolutePath();
        System.out.println(url);

        drivers.forEach(i->{
            i.get(url);
        });

        Instant end = Instant.now().plusSeconds(60);
        drivers.forEach(driver -> {
            (new WebDriverWait(driver, 90)).until(
                    (ExpectedCondition<Boolean>) d -> Instant.now().isAfter(end)
            );
        });
        ctx.stop();


        Thread.sleep(30000);

        drivers.forEach(i -> {
                    String result = i.findElement(By.id("status")).getText();
                    logger.info(result);

                    String error = i.findElement(By.id("error")).getText();
                    logger.info(error);
                }
        );

        rec.forEach((conn,data) -> {
                    logger.info("Connection {} got {} messages",conn,data.size());
                    long max = 0;
                    long accum = 0;
                    List<Long> sorted = data.stream()
                            .map(i->i.time).sorted().collect(Collectors.toList());
                    for(int i = 1; i < sorted.size(); i++) {
                        long diff = sorted.get(i) - sorted.get(i-1);
                        max = Math.max(diff,max);
                        accum += diff;
                    }
                    logger.info("Max latency {} average {}",max,accum/(sorted.size()-1));

                });

        rec.values().stream().findFirst().ifPresent(data->{
            float[][] inDat = new float[2][data.size()-1];

            List<Long> sorted = data.stream()
                    .map(i->i.time).sorted().collect(Collectors.toList());
            for(int i = 1; i < sorted.size(); i++) {
                long diff = sorted.get(i) - sorted.get(i-1);
                inDat[0][i-1] = (float) i;
                inDat[1][i-1] = (float) diff;
            }

            FastScatterPlot scatter = new FastScatterPlot(inDat, new NumberAxis("X"), new NumberAxis("Y"));
            scatter.setData(inDat);

            PlotView pl = new PlotView(scatter);
            pl.saveChart(new File(chartFile.getAbsolutePath()+"/chart"+ LocalDateTime.now().toString()+".png"));
            /*pl.pack();
            pl.setVisible(true);
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/

        });



    }

}

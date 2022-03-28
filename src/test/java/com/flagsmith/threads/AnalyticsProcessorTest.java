package com.flagsmith.threads;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flagsmith.FlagsmithApiWrapper;
import com.flagsmith.FlagsmithException;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.config.Retry;
import java.io.IOException;
import okhttp3.Response;
import okhttp3.mock.MockInterceptor;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups="unit")
public class AnalyticsProcessorTest {

  private FlagsmithApiWrapper api;
  private FlagsmithLogger flagsmithLogger;
  private FlagsmithConfig defaultConfig;
  private MockInterceptor interceptor;
  private RequestProcessor requestProcessor;
  private AnalyticsProcessor analytics;

  @BeforeMethod(groups = "unit")
  public void init() {
    flagsmithLogger = mock(FlagsmithLogger.class);
    doThrow(new FlagsmithException("error Response")).when(flagsmithLogger)
        .httpError(any(), any(Response.class), eq(true));
    doThrow(new FlagsmithException("error IOException")).when(flagsmithLogger)
        .httpError(any(), any(IOException.class), eq(true));

    interceptor = new MockInterceptor();
    defaultConfig = FlagsmithConfig.newBuilder().addHttpInterceptor(interceptor)
        .retries(new Retry(1)).baseUri("http://base-uri/")
        .build();
    api = mock(FlagsmithApiWrapper.class);
    requestProcessor = mock(RequestProcessor.class);
    when(api.getConfig()).thenReturn(defaultConfig);
    analytics = new AnalyticsProcessor(api, flagsmithLogger, requestProcessor);
  }

  public void AnalyticsProcessor_checkAnalyticsData() {
    analytics.trackFeature(1);
    Assert.assertTrue(analytics.getAnalyticsData().containsKey(1));
    Assert.assertEquals(analytics.getAnalyticsData().size(), 1);

    analytics.trackFeature(1);
    Assert.assertEquals(analytics.getAnalyticsData().size(), 1);
    Assert.assertEquals(analytics.getAnalyticsData().get(1).intValue(), 2);
  }

  public void AnalyticsProcessor_checkAnalyticsDataCheckFlushRuns() throws InterruptedException {
    Long nextFlush = analytics.getNextFlush();
    analytics.trackFeature(1);
    Assert.assertEquals(nextFlush, analytics.getNextFlush());
    Thread.sleep(11000);
    analytics.trackFeature(1);
    Assert.assertEquals(analytics.getAnalyticsData().size(), 0);
    Assert.assertNotEquals(nextFlush, analytics.getNextFlush());
    verify(api, times(1)).newPostRequest(any(), any());
    verify(requestProcessor, times(1)).executeAsync(any(), any());
  }

  public void AnalyticsProcessor_checkAnalyticsRequestCheckFlushRuns() {
    Long nextFlush = analytics.getNextFlush();
    analytics.trackFeature(1);
    Assert.assertEquals(nextFlush, analytics.getNextFlush());

    analytics.flush();
    verify(api, times(1)).newPostRequest(any(), any());
    verify(requestProcessor, times(1)).executeAsync(any(), any());
  }
}

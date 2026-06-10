package rikser123.yandexfetcher;

import rikser123.bundle.dto.request.RikserRequestItem;

public class IntegrationUtils {
  public static <T> RikserRequestItem<T> buildRequest(T body) {
    var request = new RikserRequestItem();
    request.setData(body);
    request.setChannel("channel");

    return request;
  }
}

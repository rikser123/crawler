package rikser123.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.BothubRequestDto;
import rikser123.crawler.feign.BothubClient;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class BothubService {
  private final BothubClient bothubClient;

  @Value("${bothub.token}")
  private String bothubToken;

  @Value("${bothub.summary_model}")
  private String bothubSummaryModel;

  public String getSummary(List<String> chunks) {
    var prompt = String.format("""
      Инструкция: Ты — профессиональный анализатор текста. Составь краткий структурированный конспект статьи для дальнейшего использования в аналитической системе.
      Задача: Извлеки из текста всю значимую информацию и представь её в виде краткого, фактологического конспекта. Конспект должен быть максимально информативным, но при этом занимать не более 500–700 слов.
      Структура конспекта (строго соблюдай):
      Основная тема: 1 предложение, формулирующее главную тему статьи.
      Ключевые тезисы: список из 4–7 пунктов, содержащих самые важные утверждения, факты, цифры, выводы. Каждый пункт — 1–2 предложения. Используй маркированный список.
      Детали: (если есть) важные нюансы, уточнения, исключения, которые дополняют основные тезисы. Кратко, 2–3 предложения.
      Источники и данные: если в статье есть ссылки на исследования, даты, имена, статистика — обязательно укажи.
      Важно: Пиши только по тексту. Никаких своих оценок, обобщений или домыслов. Стиль — нейтральный, деловой, без воды.
      Текст статьи:
      %s
      """, String.join(", ", chunks));
    return fetchModelRequest(prompt, bothubSummaryModel);
  }

  private String fetchModelRequest(String prompt, String model) {
    var requestDto = new BothubRequestDto();
    requestDto.setModel(model);
    requestDto.setInput(prompt);

    var response = bothubClient.getResponses(requestDto, "Bearer " + bothubToken);
    if (!Objects.isNull(response.getError())) {
      log.warn("Не удалось получить ответ от {}", model);
      throw new IllegalStateException("Не удалось определить релевантные чанки");
    }
    return response.getOutputText();
  }
}

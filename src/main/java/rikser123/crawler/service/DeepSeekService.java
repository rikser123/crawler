package rikser123.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.deepseek.DeepSeekRequestDto;
import rikser123.crawler.feign.DeepSeekClient;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepSeekService {
  private final DeepSeekClient deepSeekClient;

  @Value("${deepseek.token}")
  private String deepSeekToken;

  @Value("${deepseek.summary_model}")
  private String deepSeekSummaryModel;

  @Value("${deepseek.analysis_model}")
  private String deepSeekAnalysisModel;

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
    return fetchModelRequest(prompt, deepSeekSummaryModel);
  }

  public String getQueryAnalysis(String userQuery, List<String> summaries) {
    var promt = String.format("""
      Проанализируй запрос пользователя и пересказы сайтов из выдачи Яндекса.
            
      Запрос: %s
            
      Источники:
      %s
            
      Задачи:
      1. Выдели ключевую информацию из всех источников
      2. Объедини факты в единый связный ответ
      3. Если в источниках есть расхождения — возьми наиболее логичную/подтвержденную версию (или укажи, что мнения различаются, но не акцентируй на этом внимание)
      4. Дай структурированный ответ, который полностью закрывает запрос пользователя
            
      Формат ответа:
      ## Краткий ответ
      [1-2 предложения]
            
      ## Подробно
      [Развернутый ответ со всей важной информацией]
            
      ## Дополнительно (если нужно)
      [Нюансы, советы, важные детали]
            
      ## Откуда информация
      [Краткое указание источников]
      """, userQuery, String.join(", ", summaries));
    return fetchModelRequest(promt, deepSeekAnalysisModel);
  }

  private String fetchModelRequest(String prompt, String model) {
    var requestDto = new DeepSeekRequestDto();
    requestDto.setModel(model);
    requestDto.setMessages(List.of(new DeepSeekRequestDto.Message("user", prompt)));

    try {
      var response = deepSeekClient.getResponses(requestDto, "Bearer " + deepSeekToken);
      if (!Objects.isNull(response.getError())) {
        log.warn("Не удалось получить ответ от", model);
        throw new IllegalStateException("Не удалось получить ответ модели");
      }
      return response.getContent();

    } catch (Exception e) {
      log.warn("Не удалось получить ответ от {}", model, e);
      throw new IllegalStateException("Не удалось получить ответ модели");
    }

  }
}

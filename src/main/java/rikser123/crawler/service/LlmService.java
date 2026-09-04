package rikser123.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.llm.LlmRequestDto;
import rikser123.crawler.feign.LlmClient;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {
  private final LlmClient llmClient;

  @Value("${llm.token}")
  private String llmToken;

  @Value("${llm.summary_model}")
  private String llmSummaryModel;

  @Value("${llm.aggregation_model}")
  private String llmAggregationModel;

  @Value("${llm.analysis_model}")
  private String llmAnalysisModel;

  public String getSummary(List<String> chunks) {
    var prompt = String.format("""
      Ты — экстрактор фактов. Сожми текст до 500–1000 слов.
       ПРАВИЛА:
       1. Сохрани ВСЕ: цифры, даты, проценты, имена, названия
       2. Сохрани до 5 ключевых цитат ДОСЛОВНО
       3. Удали повторы, воду, обобщения
       4. НЕ добавляй "автор считает" — только факты
       ФОРМАТ ВЫВОДА:
       Суть: (1 предложение)
       Факты:
       - факт 1
       - факт 2
       Аргументы и выводы:
       - аргумент 1
       - вывод
       Цитаты:
       "цитата 1"
       "цитата 2"
       Детали: (важные нюансы)
       Обрати внимание, что в конце статьи есть источник. Вставь его также в конец для последующего шага.
      %s
      """, String.join(", ", chunks));
    return fetchModelRequest(prompt, llmSummaryModel);
  }

  public String getAggregationReport(List<String> summaries) {
    var aggregatorPrompt = String.format("""
      Ты — агрегатор. У тебя 30–50 пересказов на одну тему.
      Создай JSON-отчёт по схеме ниже. Все данные — ТОЛЬКО из пересказов.
      В конца каждого пересказа есть источник. Используй его в виде source для вставки в массив url
      === СХЕМА JSON ===
      {
        "topic": "строка (главная тема, 1 предложение)",
        "consensus": [
          {
            "claim": "строка (утверждение из >60%% источников)",
            "sources_count": "число",
            "sources": ["массив строк (URL)"]
          }
        ],
        "contradictions": [
          {
            "question": "строка (вопрос, по которому есть разногласия)",
            "group_a": {
              "position": "строка (позиция группы А)",
              "sources": ["массив строк (URL)"],
              "count": "число"
            },
            "group_b": {
              "position": "строка (позиция группы Б)",
              "sources": ["массив строк (URL)"],
              "count": "число"
            }
          }
        ],
        "unique_facts": [
          {
            "fact": "строка (факт из 1-2 источников)",
            "source": "строка (URL)",
            "importance": "high | medium | low"
          }
        ],
        "clusters": {
          "название_кластера": ["массив строк (URL)"]
        },
        "timeline": {
          "events": [
            {
              "date": "строка (дата из текста, если есть)",
              "event": "строка (событие)",
              "sources_count": "число"
            }
          ],
          "trends": ["массив строк (тренды)"]
        },
        "entities": {
          "название_сущности": {
            "mentions": "число",
            "claims": ["массив строк (утверждения)"]
          }
        },
        "missing_gaps": ["массив строк (вопросы без ответа)"],
        "generated_questions": ["массив строк (уточняющие вопросы для пользователя)"]
      }
      === ПРАВИЛА ===
      1. Все факты ТОЛЬКО из пересказов — НЕ ВЫДУМЫВАЙ
      2. Для каждого утверждения указывай источники (URL)
      3. Если данных нет — пиши null
      4. Названия кластеров придумай сам, отражая суть позиции
      5. Все числа — реальные цифры из данных
      Пересказы:
      %s
      """, String.join(" ,", summaries));
    return fetchModelRequest(aggregatorPrompt, llmAggregationModel);
  }

  public String getQueryAnalysis(String userQuery, String aggregationReport) {
    var prompt = String.format("""
      Ты — аналитик. У тебя есть JSON-отчёт по 30–50 источникам.
      Создай структурированный ответ пользователю.
      Каждый пересказ в конце содержит источник в виде урла. Используй его в тексте как ссылку для сносок.
      === ЗАПРОС ПОЛЬЗОВАТЕЛЯ ===
      %s
      === JSON-ОТЧЁТ ===
      %s
      === СТРУКТУРА ОТВЕТА ===
      ## Главный вывод
      (2-3 предложения, самый важный инсайт из всех источников)
      ## Детальный анализ по темам
      (Разбей на 3-5 логических блоков)
      - Тема 1: факты, количество источников, примеры URL
      - Тема 2: факты, количество источников, примеры URL
      ## Противоречия и спорные моменты
      (Где источники расходятся?)
      - Вопрос: ...
        - Группа А (X источников): позиция → URL
        - Группа Б (Y источников): позиция → URL
      ## Уникальные инсайты
      (Что нашли только в 1-2 источниках?)
      - Инсайт 1 → источник
      - Инсайт 2 → источник
      ## Ключевые игроки и их позиции
      (Компании/персоны, которые упоминаются чаще всего)
      - Игрок 1: позиция, ключевые заявления
      - Игрок 2: позиция, ключевые заявления
      ## Хронология и тренды
      (Как менялась ситуация во времени?)
      - Ключевые события по датам
      - Основные тренды
      ## Чего не хватает
      (Какие вопросы остались без ответа?)
      ## Рекомендации и следующие шаги
      (Что делать на основе анализа?)
      - Рекомендация 1
      - Уточняющий вопрос для пользователя
      === ПРАВИЛА ===
      1. Каждый факт подтверждай источниками: [Источник: url.com]
      2. Для противоречий: [Группа А: url1, url2 | Группа Б: url3, url4]
      3. НЕ ВЫДУМЫВАЙ факты, которых нет в отчёте
      4. Будь конкретен: цифры, даты, имена
      5. Если данных для раздела нет — пропусти его
      6. В конце не надо блок Уточняющий вопрос для пользователя
      7. Сразу делай красивое форматирование, а не кучей текст.
      8. При анализе источников укажи их ранжирование по авторитетности
      """, userQuery, aggregationReport);
    return fetchModelRequest(prompt, llmAnalysisModel);
  }

  private String fetchModelRequest(String prompt, String model) {
    var requestDto = new LlmRequestDto();
    requestDto.setModel(model);
    requestDto.setMessages(List.of(new LlmRequestDto.Message("user", prompt)));

    try {
      var response = llmClient.getResponses(requestDto, "Bearer " + llmToken);
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

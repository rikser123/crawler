package rikser123.crawler.service;

import feign.Request;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.llm.LlmRequestDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.feign.LlmClient;

import java.time.Duration;
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

  @Value("${llm.analysis_model}")
  private String llmAnalysisModel;

  @Value("${llm.synthesis_model}")
  private String llmSynthesisModel;

  @Value("${llm.summary-timeout}")
  private Integer summaryTimeout;

  private final Request.Options llmOptions;

  @Retryable(
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
  )
  public String getSummary(List<String> chunks) {
    var summaryOptions = new Request.Options(Duration.ofSeconds(5), Duration.ofSeconds(summaryTimeout), false);
    var prompt = String.format("""
      Ты — экстрактор фактов и аргументов. Из текста ниже извлеки атомарные утверждения и их контекст.

      === СХЕМА ===
      {
        "source_url": "URL источника (скопировать дословно из конца текста)",
        "topic": "тема текста, 1 предложение",
        "summary": "сжатый пересказ, 1–2 предложения",
        "claims": [
          {
            "text": "атомарное утверждение (одна мысль)",
            "type": "fact | statistic | date | forecast | opinion | definition",
            "entities": ["организации, персоны, продукты"],
            "date": "дата события или публикации",
            "assumptions": ["допущения, на которых держится утверждение"],
            "confidence": "явно | предположительно | оценочно"
          }
        ]
      }

      === ЧТО ИЗВЛЕКАТЬ ===
      Только утверждения, которые влияют на ответ пользователю:
      - конкретные факты с числами, датами, именами
      - статистика и метрики
      - прогнозы и оценки с указанием, кто их даёт
      - прямые цитаты ключевых фигур (в поле text, если это важно)
      - определения, если они раскрывают суть темы
      - противоречия между авторами (оформи как два отдельных claim)

      === ЧТО НЕ ИЗВЛЕКАТЬ ===
      - описание структуры статьи («в этой статье мы рассмотрим»)
      - общеизвестные факты без контекста («Биткоин — это криптовалюта»)
      - рекламные формулировки, призывы, ссылки на другие материалы
      - сведения об авторе статьи (если автор не является субъектом темы)
      - повторы одной и той же мысли разными словами — оставляй один claim
      - вводные конструкции и «воду»

      === ПРАВИЛА ===
      1. Целевой объём: 10–25 claims. Если в тексте только 5 значимых
         утверждений — верни 5, не добивай до 25 ради числа.
      2. Каждый claim атомарен. «Вырос на 20%% и планирует IPO» → два claim.
      3. Для каждого forecast ОБЯЗАТЕЛЬНО заполни assumptions.
         Если в тексте допущение не указано явно — выведи его сам
         и пометь [выведено].
      4. Сохраняй все цифры, даты, имена дословно. Не перефразируй числа.
      5. Если два утверждения противоречат друг другу — оставь оба,
         но пометь в text: «[противоречие с ...]».
      6. Никакого текста вне JSON. Без markdown-обёрток.

      Текст:
      %s
    """, String.join("\n\n---\n\n", chunks));

    var llmRequest = LlmRequestDto.builder().
      model(llmSummaryModel)
      .maxTokens(5000)
      .temperature(0.1)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .responseFormat(new LlmRequestDto.ResponseFormat("json_object"))
      .build();

    return fetchModelRequest(llmRequest, summaryOptions);
  }

  public String getClusters(String userQuery, List<SearchResponseDtoWithContent> searchDtos) {
    var passages = searchDtos.stream()
      .map(dto -> dto.getSearchResponse().getPassages() + "\\n источник " + dto.getSearchResponse().getUrl())
      .toList();

    var prompt = String.format("""
      Ты — классификатор. Запрос пользователя: %s

      На входе — JSON-массив кратких текстов для каждого источника.
      Сгруппируй их в 3–7 смысловых кластеров по темам.
  
      === СХЕМА ===
      {
        "clusters": [
          {
            "name": "название кластера (2–4 слова)",
            "essence": "суть кластера, 1–2 предложения",
            "sources": ["массив source_url"],
            "key_claims": ["3–5 самых важных утверждений кластера"],
          }
        ]    
      }
  
      === ПРАВИЛА ===
      1. Каждый source_url попадает ровно в один кластер.
      2. Кластеры должны отражать СМЫСЛ, а не типы источников.
      3. Если источник не подходит ни к одному кластеру — создай кластер «Прочее».
      4. key_claims — самые цитируемые/важные утверждения, не пересказ.
      5. Верни только JSON.
      6. source_url в конце пассажа отмечен как источник.
  
      Входной массив:
      %s
      """, userQuery, String.join("\n\n---\n\n", passages));

    log.info("getClusters");

    var llmRequest = LlmRequestDto.builder().
       model(llmSummaryModel)
      .maxTokens(5000)
      .temperature(0.1)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .responseFormat(new LlmRequestDto.ResponseFormat("json_object"))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  public String getAnalysisCluster(String userQuery,String cluster, List<SearchResponseDtoWithContent> contents) {
    var summaries = contents.stream().map(content -> content.getContent()).toList();
    var prompt = String.format("""
      Ты — аналитик. Запрос пользователя: %s
        
      Вот кластер источников на одну тему. Каждый источник — JSON с полями
      source_url, summary, claims. Твоя задача — проанализировать,
      а не пересказать.

      === ОТВЕТЬ НА ВОПРОСЫ ===
      1. ФАКТЫ. Что установлено как факт? С какими source_url?
      2. ПРОГНОЗЫ. Что прогнозируется? На каких допущениях? С source_url.
         Если допущение не указано — выведи и пометь [выведено].
      3. ПРОТИВОРЕЧИЯ. Позиция A (source_url) vs позиция B (source_url),
         в чём суть, чем объяснимо.
      4. МЕХАНИЗМЫ. Причинно-следственные связи: где объяснено,
         где только корреляция. С source_url.
      5. СЛАБЫЕ МЕСТА. Что держится на одном источнике или на блоге,
         где автор противоречит себе. С source_url.
      6. ЧЕГО НЕ ХВАТАЕТ. Какие вопросы без ответа.
      7. ГЛАВНОЕ. Что самое важное в кластере, 2–3 предложения.

      === ПРАВИЛА ===
      1. ЦИФРЫ, ДАТЫ, ИМЕНА — ДОСЛОВНО из поля claims. Не округлять,
         не перефразировать. «12,4 млрд» → «12,4 млрд».
      2. Каждое утверждение — с source_url. Без source_url не включать.
      3. Не выдумывать факты, которых нет во входе.
      4. Если данных нет — «нет данных», не обтекаемая фраза.
      5. Формат — markdown. Структура — по вопросам.

      Кластер:
      %s
      Источники:
      %s
    """, userQuery, cluster, String.join("\n\n---\n\n", summaries));

    log.info("getAnalysisCluster");


    var llmRequest = LlmRequestDto.builder().
      model(llmSummaryModel)
      .maxTokens(5000)
      .temperature(0.3)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  public String getClustersSynthesis(String userQuery, List<String> clusters) {
    var prompt = String.format("""
       Ты — синтетик. Запрос пользователя: %s
      
        У тебя есть анализы по N кластерам. Найди то, что НЕ ВИДНО
        в отдельных кластерах. Это самое ценное в анализе.
  
        === ОТВЕТЬ НА ВОПРОСЫ ===
        1. ОБЩИЕ ДОПУЩЕНИЯ. Какие допущения лежат в основе сразу нескольких
           кластеров? Если допущение неверно — какие выводы рушатся?
        2. СВЯЗИ. Что из кластера A подтверждается или опровергается
           кластером B? Конкретные пары с source_url обеих сторон.
        3. СЛЕДСТВИЯ. Что следует из комбинации фактов, хотя ни в одном
           кластере не сформулировано явно? Помечай [синтез].
           ОБЯЗАТЕЛЬНО: минимум 2–3 пункта [синтез]. Это ядро отчёта.
        4. МЕХАНИЗМЫ. Причинно-следственные связи через несколько кластеров.
        5. ПРОТИВОРЕЧИЯ МЕЖДУ КЛАСТЕРАМИ. Где один кластер утверждает то,
           что другой отрицает. С source_url обеих сторон.
        6. ОБЩАЯ КАРТИНА. Какой вывод складывается из всего, 3–5 предложений.
  
        === ПРАВИЛА ===
        1. Цифры — ДОСЛОВНО из анализов кластеров, не округлять.
        2. Каждая связь — с source_url обеих сторон или [синтез].
        3. Не повторяй то, что было в кластерах. Только связи и следствия.
        4. Если связи нет — не выдумывай.
        5. Формат — markdown.
  
        Анализы кластеров:
        %s
        """, userQuery, String.join("\n\n---\n\n", clusters));

    log.info("getClustersSynthesis: {}");


    var llmRequest = LlmRequestDto.builder().
       model(llmSynthesisModel)
      .enableThinking(true)
      .reasoningEffort("medium")
      .maxTokens(6000)
      .temperature(0.5)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  public String getCritic(String userQuery, String synthesis) {
    var prompt = String.format("""
         Ты — скептик. Запрос пользователя: %s
        
        Твоя задача — атаковать выводы ниже. Не защищай, не балансируй.
  
        === ФОРМАТ ОТВЕТА (строго) ===
        Для каждого слабого места:
        [УТВЕРЖДЕНИЕ] <цитата из синтеза с source_url или [синтез]>
        ВЕРДИКТ: выдержало / ослабло / рухнуло
        ПРИЧИНА: <обоснование, 1–2 предложения>
  
        === ЧТО ИСКАТЬ ===
        1. Выводы на ненадёжных источниках (один источник, блог).
        2. Противоречия внутри синтеза.
        3. Альтернативные объяснения, которые не рассмотрены.
        4. Непроверенные допущения.
        5. Корреляция, выданная за причинность.
        6. [синтез] без опоры на конкретные source_url.
        7. Что могло бы опровергнуть выводы.
  
        === ПРАВИЛА ===
        1. Не защищай. Только атакуй.
        2. Каждая атака — с обоснованием, не «мне кажется».
        3. Если вывод выдерживает атаку — ВЕРДИКТ: выдержало.
        4. Формат строго как выше. Без прозы.
  
        Синтез:
        %s
        """, userQuery, synthesis);

    log.info("getCritic");


    var llmRequest = LlmRequestDto.builder().
       model(llmSynthesisModel)
      .enableThinking(true)
      .reasoningEffort("medium")
      .maxTokens(5000)
      .temperature(0.6)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  public String getAnalysis(String userQuery, String synthesis, String critic, List<String> clusters) {
      var prompt = String.format("""
      Ты — старший аналитик. Ты пишешь отчёт для человека, который принимает
       решения и платит за анализ. Он не хочет читать пересказ источников —
       он хочет выводы, которым можно доверять, и увидеть то, что не видно
       при беглом чтении.
  
       Отчёт должен отвечать на три вопроса читателя:
       1. Что мне нужно знать? (за 30 секунд)
       2. Почему я должен этому верить? (за 2 минуты)
       3. Что я с этим делаю? (за 5 минут)
  
       === ЗАПРОС ЧИТАТЕЛЯ ===
       %s
  
       === ФОРМАТ ОТЧЁТА ===
  
       ┌─ ГЛАВНОЕ ─────────────────────────────────────────────
       │ 2–3 предложения. Прямой ответ. Без «источники утверждают»,
       │ без «в поле доминирует». Как ответил бы эксперт.
       │ Если ответа нет — прямо в первой строке: «Ответить нельзя,
       │ потому что ...».
       └───────────────────────────────────────────────────────
  
       УВЕРЕННОСТЬ: высокая / средняя / низкая
       Почему: одна строка — на скольких независимых линиях держится,
       где согласие, где слабое место.
  
       ┌─ КЛЮЧЕВЫЕ ВЫВОДЫ ─────────────────────────────────────
       │ 3–5 пунктов. Каждый — одна мысль, 1–2 предложения.
       │ Что установлено + на чём держится [source_url] + метка.
       │
       │ Метки:
       │ [ТВЁРДО] — ≥3 независимых источника, есть первоисточники
       │ [ВЕРОЯТНО] — 2 независимых или 1 первоисточник
       │ [СПОРНО] — есть противоречащие версии, рядом обе
       │ [СЛАБО] — 1 источник, блог, без первоисточника
       │ [АНГАЖИР] — у источника виден интерес
       └───────────────────────────────────────────────────────
  
       ┌─ ПОЧЕМУ ТАК ──────────────────────────────────────────
       │ Развёрнутое обоснование ключевых выводов. 400–700 слов.
       │ По каждой смысловой линии: что утверждается, кто
       │ [source_url], сколько НЕЗАВИСИМЫХ источников
       │ (перепечатки = один), где согласие, где раскол.
       └───────────────────────────────────────────────────────
  
       ┌─ ЧТО НЕ ОЧЕВИДНО ─────────────────────────────────────
       │ 2–4 пункта. То, что НЕ сказано прямо ни одним источником,
       │ но следует из комбинации фактов. Помечай [синтез].
       │ Ради этого читатель платит.
       │ Если нечего сказать — «не выявлено», не выдумывать.
       └───────────────────────────────────────────────────────
  
       ┌─ ЦИФРЫ ───────────────────────────────────────────────
       │ Только если в вопросе есть «сколько / насколько / когда».
       │ Цифра | Кто называет [source_url] | Расхождения | Откуда.
       │ Цифры — ДОСЛОВНО. Конфликтующие — обе. Усреднять запрещено.
       └───────────────────────────────────────────────────────
  
       ┌─ ПРОГНОЗЫ ────────────────────────────────────────────
       │ Только если есть forecast.
       │ Что | Кто [source_url] | Допущения | Насколько надёжно.
       │ Допущения не указанные — [выведено].
       └───────────────────────────────────────────────────────
  
       ┌─ ГДЕ МОЖНО ОШИБИТЬСЯ ─────────────────────────────────
       │ 3–5 пунктов. Слабые места отчёта и поля:
       │ - на одном источнике [СЛАБО];
       │ - ангажированные [АНГАЖИР];
       │ - противоречия без объяснения;
       │ - прогнозы без допущений;
       │ - [синтез] без опоры на факты;
       │ - клоны, выданные за независимые.
       │ Это раздел честности. Он продаёт не меньше, чем ГЛАВНОЕ.
       └───────────────────────────────────────────────────────
  
       ┌─ ЧЕГО НЕ ХВАТАЕТ ─────────────────────────────────────
       │ Что отсутствует, чем это блокирует ответ,
       │ каким источником или запросом закрыть.
       └───────────────────────────────────────────────────────
  
       ┌─ ЧТО ДЕЛАТЬ ──────────────────────────────────────────
       │ 2–4 конкретных действия из отчёта:
       │ что проверить, кого спросить, какое решение принять.
       │ Если действий нет — раздел не включать.
       └───────────────────────────────────────────────────────
  
       ┌─ ИСТОЧНИКИ ───────────────────────────────────────────
       │ source_url | что взято (кратко)
       └───────────────────────────────────────────────────────
  
       === ПРАВИЛА ===
       1. ГЛАВНОЕ — ответ, а не описание. Если первый абзац начинается
          с «источники утверждают» — переписать.
       2. Каждое утверждение — с source_url из материалов. Ничего сверх
          материалов: ни URL, ни цифр, ни событий.
       3. Перепечатки и клоны — один источник. 10 сайтов с одним
          текстом = 1 источник.
       4. Цифры — ДОСЛОВНО. «Около 12 млрд» → «около 12 млрд».
       5. Противоречия не сглаживать. Обе позиции рядом.
       6. [синтез] — вывод из комбинации фактов, не сформулированный
          ни одним источником. Каждый [синтез] — с опорой на source_url.
       7. Тон прямой. Запрещены «важно отметить», «стоит подчеркнуть».
          Плотность. Ориентир 1200–2000 слов.
       8. Критика имеет вес: если критик пометил «рухнуло» — не включать,
          «ослабло» — с оговоркой.
       9. Формат — markdown, заголовки как в шаблоне.
       10. Разделы ЦИФРЫ, ПРОГНОЗЫ, ЧТО ДЕЛАТЬ — только если есть
           содержание. Пустых не делать.
  
       === САМОПРОВЕРКА ===
       - ГЛАВНОЕ отвечает на вопрос за 30 секунд?
       - Каждый вывод с source_url и меткой надёжности?
       - Есть хотя бы один [синтез]?
       - Цифры дословные?
       - Противоречия видны?
       - Критика учтена?
       - Если убрать всё, что можно найти за 10 минут в Гугле —
         останется ли ценность?
  
       === СИНТЕЗ ===
       %s
  
       === КРИТИКА ===
       %s
  
       === АНАЛИЗЫ КЛАСТЕРОВ ===
       %s
    """, userQuery, synthesis, critic, clusters);

    log.info("getAnalysis: {}");


    var llmRequest = LlmRequestDto.builder().
      model(llmAnalysisModel)
      .enableThinking(true)
      .reasoningEffort("medium")
      .maxTokens(8000)
      .temperature(0.3)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  private String fetchModelRequest(LlmRequestDto requestDto, Request.Options options) {
    var model = requestDto.getModel();

    try {
      var response = llmClient.getResponses(requestDto, "Bearer " + llmToken, options);
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

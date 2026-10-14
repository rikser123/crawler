package rikser123.crawler.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rikser123.bundle.dto.response.RikserResponseItem;
import rikser123.crawler.dto.customPrompt.CustomPromptFilterDto;
import rikser123.crawler.dto.customPrompt.CustomPromptListResponseDto;
import rikser123.crawler.dto.customPrompt.CustomPromptRequestDto;
import rikser123.crawler.dto.customPrompt.CustomPromptResponseDto;
import rikser123.crawler.dto.customPrompt.CustomPromptSuggestionResponseDto;
import rikser123.crawler.service.CustomPromptService;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "API для взаимодействия с пользовательскими промптами")
@ApiResponses(
  value = {
    @ApiResponse(responseCode = "400", description = "Bad request"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
@RequestMapping("/api/v1/custom-prompt")
public class CustomPromptController {
  private final CustomPromptService customPromptService;

  @Operation(description = "Получение списка пользовательских запросов")
  @GetMapping("/list")
  @PreAuthorize("hasAuthority('CUSTOM_PROMPT')")
  public RikserResponseItem<CustomPromptListResponseDto> getList(@ModelAttribute CustomPromptFilterDto filterDto) {
    return customPromptService.getUserPrompts(filterDto);
  }

  @PostMapping
  @Operation(description = "Сохранение пользовательского промпта")
  @PreAuthorize("hasAuthority('CUSTOM_PROMPT')")
  public RikserResponseItem<CustomPromptResponseDto> save(
    @RequestBody
    @Parameter(description = "Параметры для сохранения промпта", required = true)
    CustomPromptRequestDto requestDto) {
    return customPromptService.save(requestDto);
  }

  @PostMapping("/suggestion")
  @Operation(description = "Получение анализа пользовательского промпта от ллм")
  @PreAuthorize("hasAuthority('CUSTOM_PROMPT')")
  public RikserResponseItem<CustomPromptSuggestionResponseDto> getSuggestion(
    @RequestBody
    @Parameter(description = "Параметры для анализа пользовательского промпта", required = true)
    CustomPromptRequestDto requestDto) {
    return customPromptService.getPromptSuggestion(requestDto);
  }
}

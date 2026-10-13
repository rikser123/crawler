package rikser123.crawler.mapper;

import org.mapstruct.Mapper;
import rikser123.crawler.dto.customPrompt.CustomPromptRequestDto;
import rikser123.crawler.dto.customPrompt.CustomPromptResponseDto;
import rikser123.crawler.repository.entity.CustomPrompt;

@Mapper(componentModel = "spring")
public interface CustomPromptMapper {
  CustomPromptResponseDto mapToDto(CustomPrompt entity);

  CustomPrompt mapToEntity(CustomPromptRequestDto dto);
}

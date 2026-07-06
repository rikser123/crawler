package rikser123.crawler.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import rikser123.crawler.dto.MessageUserQueryDto;
import rikser123.crawler.dto.SearchResponseDto;
import rikser123.crawler.dto.SearchResponseDtoStatus;

@Mapper(componentModel = "spring")
public interface SearchResponseMapper {
  SearchResponseDto mapToDtoFromMessage(MessageUserQueryDto.SearchResponse searchResponse);

  @AfterMapping
  default void afterFromMessage(
    MessageUserQueryDto.SearchResponse searchResponse,
    @MappingTarget SearchResponseDto searchResponseDto
  ) {
    searchResponseDto.setStatus(SearchResponseDtoStatus.CREATED);
  }
}

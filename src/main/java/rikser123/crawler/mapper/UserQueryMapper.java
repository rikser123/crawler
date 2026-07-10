package rikser123.crawler.mapper;

import lombok.Setter;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import rikser123.crawler.dto.userQuery.MessageUserQueryDto;
import rikser123.crawler.dto.queryResponse.QueryResponseDtoStatus;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.dto.userQuery.UserQueryDto;

@Mapper(componentModel = "spring")
public abstract class UserQueryMapper {
  @Setter(onMethod = @__({@Autowired}))
  private SearchResponseMapper searchResponseMapper;

  @Mapping(source = "searchResponses", target = "searchResponses", ignore = true)
  public abstract UserQueryDto mapMessageToDto(MessageUserQueryDto dto);

  @AfterMapping
  void afterMapMessageToDto(MessageUserQueryDto message, @MappingTarget UserQueryDto dto) {
    var responses = message.getSearchResponses();
    var dtoResponses = responses.stream().map(searchResponseMapper::mapToDtoFromMessage).peek(response -> {
      response.setQueryId(message.getSearchQueryId());
      response.setQueryText(dto.getQueryText());
    }).map(response  -> {
      var processedResponse = new SearchResponseDtoWithContent();
      processedResponse.setSearchResponse(response);
      processedResponse.setStatus(QueryResponseDtoStatus.CREATED);
      return processedResponse;
    }).toList();
    dto.setSearchResponses(dtoResponses);
  }

}

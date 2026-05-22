package com.beefit.api.mapper;

import com.beefit.api.domain.User;
import com.beefit.api.dto.UserResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserResponseMapper {

    User toEntity(UserResponseDTO dto);

    UserResponseDTO toDto(User user);
}

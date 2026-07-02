package com.beefit.api.mapper;

import com.beefit.api.domain.User;
import com.beefit.api.dto.user.BannedUserResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BanMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "banDays", target = "banDays")
    @Mapping(source = "user.banned", target = "banned")
    @Mapping(source = "user.bannedUntil", target = "bannedUntil")
    BannedUserResponseDTO toResponseDTO(User user, Integer banDays);
}

package com.learnhub.user.service;

import com.learnhub.common.dto.PageResponse;
import com.learnhub.common.exception.BadRequestException;
import com.learnhub.common.exception.ResourceNotFoundException;
import com.learnhub.user.dto.response.WishlistResponse;
import com.learnhub.user.entity.Wishlist;
import com.learnhub.user.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WishlistService {
    private final WishlistRepository wishlistRepository;

    @Transactional(readOnly = true)
    public PageResponse<WishlistResponse> getWishlist(UUID userId, Pageable pageable) {
        Page<Wishlist> page = wishlistRepository.findByUserId(userId, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public void addToWishlist(UUID userId, UUID courseId) {
        if(wishlistRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new BadRequestException("ALREADY_IN_WISHLIST",
                    "course already have in wishlist");
        }

        Wishlist wishlist = Wishlist.builder()
                .userId(userId)
                .courseId(courseId)
                .build();
        wishlistRepository.save(wishlist);
    }

    @Transactional
    public void removeFromWishlist(UUID userId, UUID courseId) {
        if(!wishlistRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new ResourceNotFoundException("WISHLIST_ITEM_NOT_FOUND",
                    "Course does not have in wishlist");
        }
        wishlistRepository.deleteByUserIdAndCourseId(userId, courseId);
    }

    private WishlistResponse toResponse(Wishlist wishlist) {
        return WishlistResponse.builder()
                .courseId(wishlist.getCourseId())
                .addedAt(wishlist.getCreatedAt())
                .build();
    }
}

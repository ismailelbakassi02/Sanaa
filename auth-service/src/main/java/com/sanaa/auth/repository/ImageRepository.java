package com.sanaa.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.auth.domain.Image;

public interface ImageRepository extends JpaRepository<Image, String> {
}

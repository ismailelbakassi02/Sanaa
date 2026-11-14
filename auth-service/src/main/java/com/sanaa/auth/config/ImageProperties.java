package com.sanaa.auth.config;

import java.nio.file.Path;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Location of uploaded images. Defaults to a local directory so a developer checkout works without
 * configuration; production mounts a volume and sets {@code IMAGES_DIR}.
 */
@Validated
@ConfigurationProperties(prefix = "sanaa.images")
public record ImageProperties(@NotNull Path directory) {
}

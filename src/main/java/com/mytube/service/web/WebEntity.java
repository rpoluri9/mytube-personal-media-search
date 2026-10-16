package com.mytube.service.web;

/** One entity Google deduced from similar images on the web. */
public record WebEntity(String description, double score) {}

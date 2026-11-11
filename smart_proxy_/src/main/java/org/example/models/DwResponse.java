package org.example.models;

public record DwResponse(int status,
                         String contentType,
                         String body)
{ }

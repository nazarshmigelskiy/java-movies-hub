package ru.practicum.moviehub.api;

public class ErrorResponse {

    private final String error;
    private final String[] description;

    public ErrorResponse(String error, String[] description) {
        this.error = error;
        this.description = description;
    }

    public String getError() {
        return error;
    }

    public String[] getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "error='" + error + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
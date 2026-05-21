package ru.practicum.moviehub.http;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase();
        String query = ex.getRequestURI().getQuery();
        String[] pathParts = path.split("/");

        switch (method) {
            case ("GET"):
                if (path.equalsIgnoreCase("/movies")) {
                    if (query != null) {
                        getHandleYear(ex);
                    } else {
                        getHandle(ex);
                    }
                    break;
                } else if (pathParts.length == 3) {
                    getHandleId(ex);
                    break;
                }
            case ("POST"):
                postHandle(ex);
                break;
            case ("DELETE"):
                deleteHandle(ex);
                break;
            default:
                sendNoContent(ex, 405);
                break;
        }
    }

    private void getHandle(HttpExchange ex) throws IOException {
        String json = gson.toJson(store.getMovies());
        sendJson(ex, 200, json);
    }

    private void getHandleId(HttpExchange ex) throws IOException {
        Optional<Integer> optionalId = getId(ex);
        if (optionalId.isPresent() && store.findMovieById(optionalId.get()).isPresent()) {
            Movie movie = store.findMovieById(optionalId.get()).get();
            String json = gson.toJson(movie);
            sendJson(ex, 200, json);
        } else if (optionalId.isPresent() && store.findMovieById(optionalId.get()).isEmpty()) {
            String json = gson.toJson(new ErrorResponse("Фильм не найден", new String[]{}));
            sendJson(ex, 404, json);
        } else {
            String json = gson.toJson(new ErrorResponse("Некорректный ID", new String[]{}));
            sendJson(ex, 400, json);
        }
    }

    private void getHandleYear(HttpExchange ex) throws IOException {
        if (getYear(ex.getRequestURI().getQuery()).isEmpty()) {
            String json = gson.toJson
                    (new ErrorResponse("Некорректный параметр запроса — 'year'", new String[]{}));
            sendJson(ex, 400, json);
        } else {
            String json = gson.toJson(store.findMoviesByYear(getYear(ex.getRequestURI().getQuery()).get()));
            sendJson(ex, 200, json);
        }
    }

    private void postHandle(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders()
                .getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendNoContent(ex, 415);
            return;
        }
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonElement jsonElement = JsonParser.parseString(body);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String title = jsonObject.get("title").getAsString();
            int year = jsonObject.get("year").getAsInt();
            if (title.isBlank()) {
                String json = gson.toJson(new ErrorResponse("Ошибка валидации",
                        new String[]{"название не должно быть пустым"}));
                sendJson(ex, 422, json);
                return;
            }
            if (title.length() > 100) {
                String json = gson.toJson(new ErrorResponse("Ошибка валидации",
                        new String[]{"название не должно быть длиннее 100 символов"}));
                sendJson(ex, 422, json);
                return;
            }
            if (year < 1888 || year > LocalDate.now().getYear() + 1) {
                String json = gson.toJson(new ErrorResponse("Ошибка валидации",
                        new String[]{"год должен быть между 1888 и " + LocalDate.now().getYear() + 1}));
                sendJson(ex, 422, json);
                return;
            }
            Movie movie = new Movie(title, year, store.getLastId() + 1);
            store.addMovie(movie);
            String json = gson.toJson(movie);
            sendJson(ex, 201, json);
        } catch (JsonSyntaxException e) {
            String json = gson.toJson(new ErrorResponse("Некорректный JSON", new String[]{}));
            sendJson(ex, 400, json);
        }
    }

    private void deleteHandle(HttpExchange ex) throws IOException {
        Optional<Integer> optionalId = getId(ex);
        if (optionalId.isPresent()) {
            if (store.findMovieById(optionalId.get()).isPresent()) {
                store.removeMovie(store.findMovieById(optionalId.get()).get());
                sendNoContent(ex, 204);
            } else {
                String json = gson.toJson(new ErrorResponse("Фильм не найден", new String[]{}));
                sendJson(ex, 404, json);
            }
        } else {
            String json = gson.toJson(new ErrorResponse("Некорректный ID", new String[]{}));
            sendJson(ex, 400, json);
        }
    }

    private Optional<Integer> getYear(String query) {
        if (query == null || !query.startsWith("year=")) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(query.substring("year=".length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<Integer> getId(HttpExchange ex) {
        String path = ex.getRequestURI().getPath();
        String[] pathParts = path.split("/");
        Optional<Integer> optionalId;
        try {
            optionalId = Optional.of(Integer.parseInt(pathParts[2]));
        } catch (NumberFormatException e) {
            optionalId = Optional.empty();
        }
        return optionalId;
    }
}

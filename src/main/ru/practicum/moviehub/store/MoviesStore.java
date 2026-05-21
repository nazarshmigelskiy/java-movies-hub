package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class MoviesStore {
    private final List<Movie> movies = new ArrayList<>();

    public int getLastId() {
        return movies.stream()
                .mapToInt(Movie::getId)
                .max()
                .orElse(0);
    }

    public List<Movie> getMovies() {
        return movies;
    }

    public void addMovie(Movie movie) {
        movies.add(movie);
    }

    public void removeMovie(Movie movie) {
        movies.remove(movie);
    }

    public Optional<Movie> findMovieById(int id) {
        return movies.stream()
                .filter(movie -> movie.getId() == id)
                .findFirst();
    }

    public List<Movie> findMoviesByYear(int year) {
        return movies.stream()
                .filter(movie -> movie.getYear() == year)
                .toList();
    }


    public void cleanStore() {
        movies.clear();
    }
}
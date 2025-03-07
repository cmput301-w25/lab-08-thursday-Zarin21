package com.example.androidcicd.movie;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

public class MovieProvider {
    private static MovieProvider movieProvider;
    private final ArrayList<Movie> movies;
    private final CollectionReference movieCollection;

    private MovieProvider(FirebaseFirestore firestore) {
        movies = new ArrayList<>();
        movieCollection = firestore.collection("movies");
    }

    public interface DataStatus {
        void onDataUpdated();
        void onError(String error);
    }

    public interface MovieOperationCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    public static void setInstanceForTesting(FirebaseFirestore firestore) {
        movieProvider = new MovieProvider(firestore);
    }

    public void listenForUpdates(final DataStatus dataStatus) {
        movieCollection.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                dataStatus.onError(error.getMessage());
                return;
            }
            movies.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot item : snapshot) {
                    movies.add(item.toObject(Movie.class));
                }
                dataStatus.onDataUpdated();
            }
        });
    }

    public static MovieProvider getInstance(FirebaseFirestore firestore) {
        if (movieProvider == null)
            movieProvider = new MovieProvider(firestore);
        return movieProvider;
    }

    public ArrayList<Movie> getMovies() {
        return movies;
    }

    // Original method for backward compatibility
    public void updateMovie(Movie movie, String title, String genre, int year) {
        // Create a default callback that just throws exceptions on error
        updateMovie(movie, title, genre, year, new MovieOperationCallback() {
            @Override
            public void onSuccess() {
                // Do nothing on success
            }

            @Override
            public void onError(String errorMessage) {
                // Throw an exception to maintain the original behavior
                throw new IllegalArgumentException(errorMessage);
            }
        });
    }

    // New method with callback
    public void updateMovie(Movie movie, String title, String genre, int year, MovieOperationCallback callback) {
        // Check if the title has changed
        if (!movie.getTitle().equals(title)) {
            checkTitleUniqueness(title, movie.getId(), isUnique -> {
                if (isUnique) {
                    updateMovieData(movie, title, genre, year, callback);
                } else {
                    callback.onError("A movie with this title already exists");
                }
            });
        } else {
            // Title hasn't changed, just update other fields
            updateMovieData(movie, title, genre, year, callback);
        }
    }

    private void updateMovieData(Movie movie, String title, String genre, int year, MovieOperationCallback callback) {
        movie.setTitle(title);
        movie.setGenre(genre);
        movie.setYear(year);
        DocumentReference docRef = movieCollection.document(movie.getId());
        if (validMovie(movie, docRef)) {
            docRef.set(movie)
                    .addOnSuccessListener(aVoid -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onError("Failed to update movie: " + e.getMessage()));
        } else {
            callback.onError("Invalid Movie!");
        }
    }

    // Original method for backward compatibility
    public void addMovie(Movie movie) {
        // Create a default callback that just throws exceptions on error
        addMovie(movie, new MovieOperationCallback() {
            @Override
            public void onSuccess() {
                // Do nothing on success
            }

            @Override
            public void onError(String errorMessage) {
                // Throw an exception to maintain the original behavior
                throw new IllegalArgumentException(errorMessage);
            }
        });
    }

    // New method with callback
    public void addMovie(Movie movie, MovieOperationCallback callback) {
        checkTitleUniqueness(movie.getTitle(), null, isUnique -> {
            if (isUnique) {
                DocumentReference docRef = movieCollection.document();
                movie.setId(docRef.getId());
                if (validMovie(movie, docRef)) {
                    docRef.set(movie)
                            .addOnSuccessListener(aVoid -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError("Failed to add movie: " + e.getMessage()));
                } else {
                    callback.onError("Invalid Movie!");
                }
            } else {
                callback.onError("A movie with this title already exists");
            }
        });
    }

    public void deleteMovie(Movie movie) {
        DocumentReference docRef = movieCollection.document(movie.getId());
        docRef.delete();
    }

    public boolean validMovie(Movie movie, DocumentReference docRef) {
        return movie.getId().equals(docRef.getId()) && !movie.getTitle().isEmpty() && !movie.getGenre().isEmpty() && movie.getYear() > 0;
    }

    public interface TitleCheckCallback {
        void onResult(boolean isUnique);
    }

    private void checkTitleUniqueness(String title, String currentMovieId, TitleCheckCallback callback) {
        movieCollection.whereEqualTo("title", title).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        boolean isUnique = true;
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // If we're updating a movie, we need to exclude the current movie from the check
                            if (currentMovieId == null || !document.getId().equals(currentMovieId)) {
                                isUnique = false;
                                break;
                            }
                        }
                        callback.onResult(isUnique);
                    } else {
                        // If there's an error checking, assume it's unique to avoid blocking
                        callback.onResult(true);
                    }
                });
    }
}
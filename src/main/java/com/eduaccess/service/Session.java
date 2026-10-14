package com.eduaccess.service;

import com.eduaccess.model.User;
import java.util.Optional;

/** The signed-in user for this window. Cleared on sign-out. */
public final class Session {

    private User current;

    public void signIn(User user) {
        this.current = user;
    }

    public void signOut() {
        this.current = null;
    }

    public Optional<User> current() {
        return Optional.ofNullable(current);
    }

    public User require() {
        if (current == null) {
            throw new ServiceException("Please sign in first.");
        }
        return current;
    }
}

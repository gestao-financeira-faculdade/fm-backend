package com.emanudev.gestaofinanceira.shared.adapter.in.web;

import com.emanudev.gestaofinanceira.shared.domain.exception.PreconditionFailedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityTagsTest {

    @Test
    void shouldFormatVersionAsStrongEntityTag() {
        assertThat(EntityTags.of(3)).isEqualTo("\"3\"");
    }

    @Test
    void shouldAcceptMissingWildcardWeakAndListedTags() {
        assertThatCode(() -> EntityTags.requireMatch(null, 3)).doesNotThrowAnyException();
        assertThatCode(() -> EntityTags.requireMatch("*", 3)).doesNotThrowAnyException();
        assertThatCode(() -> EntityTags.requireMatch("W/\"3\"", 3)).doesNotThrowAnyException();
        assertThatCode(() -> EntityTags.requireMatch("\"2\", \"3\"", 3)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectStaleTag() {
        assertThatThrownBy(() -> EntityTags.requireMatch("\"2\"", 3)).isInstanceOf(PreconditionFailedException.class);
    }
}

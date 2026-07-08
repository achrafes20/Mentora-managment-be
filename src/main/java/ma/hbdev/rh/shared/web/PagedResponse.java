package ma.hbdev.rh.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Enveloppe de réponse paginée, compatible avec le format standard {@link ApiResponse}.
 *
 * <p>Utilisation : {@code ApiResponse.ok(PagedResponse.of(page))}.
 *
 * @param <T> type des éléments de la page
 */
public record PagedResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

  /** Construit un {@code PagedResponse} depuis un {@link Page} Spring Data. */
  public static <T> PagedResponse<T> of(Page<T> page) {
    return new PagedResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isLast());
  }
}

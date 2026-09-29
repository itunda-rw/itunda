package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.JobPost
import rw.itunda.core.domain.JobPostStatus
import java.util.Optional

interface JobPostRepository : JpaRepository<JobPost, String> {
    fun findByStatusOrderByCreatedAtDesc(status: JobPostStatus, pageable: Pageable): Page<JobPost>
    fun findByStatusAndCategoryOrderByCreatedAtDesc(status: JobPostStatus, category: String, pageable: Pageable): Page<JobPost>
    fun findByPosterIdOrderByCreatedAtDesc(posterId: String, pageable: Pageable): Page<JobPost>

    // Real proximity "near me" browse -- same bounded-candidate-set-then-Haversine-in-app
    // shape ListingRepository/CommunityPostRepository's own notes already established.
    fun findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(status: JobPostStatus): List<JobPost>

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see JobPostService.
    // myNeighborhood and User.neighborhood's own doc comments.
    fun findByStatusAndNeighborhoodOrderByCreatedAtDesc(status: JobPostStatus, neighborhood: String, pageable: Pageable): Page<JobPost>
    fun findByStatusAndNeighborhoodAndCategoryOrderByCreatedAtDesc(
        status: JobPostStatus,
        neighborhood: String,
        category: String,
        pageable: Pageable,
    ): Page<JobPost>

    // Real dual-neighborhood support (2026-08-04) -- see User.secondNeighborhood's own doc comment.
    fun findByStatusAndNeighborhoodInOrderByCreatedAtDesc(status: JobPostStatus, neighborhoods: Collection<String>, pageable: Pageable): Page<JobPost>
    fun findByStatusAndNeighborhoodInAndCategoryOrderByCreatedAtDesc(
        status: JobPostStatus,
        neighborhoods: Collection<String>,
        category: String,
        pageable: Pageable,
    ): Page<JobPost>

    // Real Karrot-Score-style trust badge input (2026-07-21) -- see
    // rw.itunda.core.trust.TrustScoreService's own doc comment; identical shape to
    // ListingRepository.countBySellerIdAndStatus.
    fun countByPosterIdAndStatus(posterId: String, status: JobPostStatus): Long

    // Real "Jobs I did" (2026-07-25) -- see ListingRepository.
    // findByBuyerIdOrderByCreatedAtDesc's own doc comment for the full account; same
    // "activity split" gap this closes, now that workerId is captured.
    fun findByWorkerIdOrderByCreatedAtDesc(workerId: String, pageable: Pageable): Page<JobPost>

    // Real bug found live (2026-08-02): JobApplicationService.apply's own
    // "existsByJobPostIdAndApplicantIdAndStatus(..., PENDING)" reject-if-already-exists
    // check reads-then-CREATEs a brand-new JobApplication row -- there's no existing
    // PENDING application for this (jobPostId, applicantId) pair to put an @Version
    // guard on yet, and job_applications has no unique constraint on
    // (job_post_id, applicant_id, status) either, so two concurrent apply() calls by the
    // same applicant to the same job post could both pass that check before either
    // committed and both create a real duplicate PENDING application. Fixed the same way
    // AccountRepository.findByIdForUpdate's own precedent works: lock the job post row
    // itself to serialize concurrent applies against it, then re-check under that lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from JobPost p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<JobPost>

    // Real short-query fallback (2026-08-14) -- see FullTextSearchUtil's own doc comment.
    @Query("SELECT p FROM JobPost p WHERE p.status = :status AND LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun searchShort(@Param("status") status: JobPostStatus, @Param("q") q: String, pageable: Pageable): Page<JobPost>

    // Real relevance-ranked full-text search (2026-08-14) -- see
    // MerchantProductRepository.searchFullText's own doc comment for the full "why".
    @Query(
        value = "SELECT p.* FROM job_posts p WHERE p.status = :#{#status.name()} " +
            "AND MATCH(p.title, p.description) AGAINST (:booleanQuery IN BOOLEAN MODE) " +
            "ORDER BY MATCH(p.title, p.description) AGAINST (:booleanQuery IN BOOLEAN MODE) DESC",
        countQuery = "SELECT COUNT(*) FROM job_posts p WHERE p.status = :#{#status.name()} " +
            "AND MATCH(p.title, p.description) AGAINST (:booleanQuery IN BOOLEAN MODE)",
        nativeQuery = true,
    )
    fun searchFullText(@Param("status") status: JobPostStatus, @Param("booleanQuery") booleanQuery: String, pageable: Pageable): Page<JobPost>
}

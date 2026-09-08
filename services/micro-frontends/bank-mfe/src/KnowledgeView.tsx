// Extracted from BankDashboard.tsx (2026-09-02, itunda-vs-Toss architecture
// comparison thread). Real 지식iN (Knowledge iN)-style open-topic Q&A -- found
// physically sitting in the middle of the Eats cluster's own code (between
// DesignatedDriverView and DineInMenuView) despite having zero relation to
// either domain, purely a chronological-addition artifact. Confirmed exclusive
// via a real per-identifier usage-count check (total-in-file minus
// uses-in-this-cluster equaled exactly 1, the import line itself, for every
// lib/knowledge export) before moving, not assumed from the misplacement alone.
import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { EmptyState } from './EmptyState';
import { ApiError, getStoredUser } from './lib/api';
import {
  adoptKnowledgeAnswer, fetchKnowledgeAnswers, fetchKnowledgeCategories, fetchKnowledgeQuestion, fetchKnowledgeQuestions,
  fetchMyKnowledgeAnswers, fetchMyKnowledgeQuestions, fetchMyKnowledgeReputation, postKnowledgeAnswer, postKnowledgeQuestion,
  type KnowledgeAnswer, type KnowledgeCategory, type KnowledgeQuestion,
} from './lib/knowledge';
import { useDeferredLoading } from './useDeferredLoading';

export function KnowledgeView() {
  const { t } = useI18n();
  const [subTab, setSubTab] = useState<'BROWSE' | 'MINE'>('BROWSE');
  const [categories, setCategories] = useState<KnowledgeCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [questions, setQuestions] = useState<KnowledgeQuestion[] | null>(null);
  const [myAnswers, setMyAnswers] = useState<KnowledgeAnswer[] | null>(null);
  const [reputation, setReputation] = useState<number | null>(null);
  const [openQuestionId, setOpenQuestionId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real pagination-discard fix (Knowledge product-completeness pass named
  // this systemic; a request never asks past page 0, so any category/list
  // with more than 20 real questions was silently unreachable beyond the
  // first page).
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    fetchKnowledgeCategories().then(setCategories).catch(() => {});
    fetchMyKnowledgeReputation().then(setReputation).catch(() => {});
  }, []);

  const load = () => {
    setError(null);
    setQuestions(null);
    setPage(0);
    setHasMore(false);
    if (subTab === 'MINE') {
      fetchMyKnowledgeQuestions(0)
        .then((r) => {
          setQuestions(r.questions);
          setHasMore(r.page + 1 < r.totalPages);
        })
        .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
      fetchMyKnowledgeAnswers().then(setMyAnswers).catch(() => {});
      return;
    }
    fetchKnowledgeQuestions(activeCategory ?? undefined, 0)
      .then((r) => {
        setQuestions(r.questions);
        setHasMore(r.page + 1 < r.totalPages);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  const loadMore = () => {
    const nextPage = page + 1;
    setLoadingMore(true);
    const fetcher = subTab === 'MINE' ? fetchMyKnowledgeQuestions(nextPage) : fetchKnowledgeQuestions(activeCategory ?? undefined, nextPage);
    fetcher
      .then((r) => {
        setQuestions((prev) => [...(prev ?? []), ...r.questions]);
        setPage(nextPage);
        setHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMore(false));
  };

  useEffect(load, [subTab, activeCategory]);

  if (openQuestionId) {
    return <KnowledgeQuestionDetailView questionId={openQuestionId} onBack={() => { setOpenQuestionId(null); load(); }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Your reputation</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{reputation ?? '…'} adopted answer{reputation === 1 ? '' : 's'}</p>
      </div>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'BROWSE' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('BROWSE')} style={{ flex: 1 }}
        >
          Browse
        </button>
        <button
          className={subTab === 'MINE' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('MINE')} style={{ flex: 1 }}
        >
          Mine
        </button>
      </div>
      {subTab === 'BROWSE' && categories.length > 0 && (
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
          <button
            className={activeCategory === null ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
            style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 12px' }} onClick={() => setActiveCategory(null)}
          >
            All
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              className={activeCategory === c.id ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
              style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 12px' }} onClick={() => setActiveCategory(c.id)}
            >
              {c.label}
            </button>
          ))}
        </div>
      )}
      {subTab === 'BROWSE' && <KnowledgeAskCard onAsked={load} categories={categories} />}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {questions === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : questions.length === 0 ? (
        // Real copy-voice fix (item 244, round 6 of the empty-state pass): specific
        // to which subtab is showing -- BROWSE has the real "Ask a question" form
        // right above, MINE doesn't (it needs to point back to BROWSE instead).
        <EmptyState message={subTab === 'BROWSE' ? 'No questions yet — ask one above.' : "You haven't asked anything yet — switch to Browse to ask your first question."} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {questions.map((q) => (
            <button
              key={q.id} className="itunda-flat-section" style={{ textAlign: 'left', width: '100%' }}
              onClick={() => setOpenQuestionId(q.id)}
            >
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>
                {q.adoptedAnswerId ? '✅ ' : ''}{q.title}
              </p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>
                {categories.find((c) => c.id === q.category)?.label ?? q.category}
              </p>
            </button>
          ))}
          {hasMore && (
            <button
              className="itunda-btn itunda-btn-secondary"
              style={{ marginTop: '8px' }}
              disabled={loadingMore}
              onClick={loadMore}
            >
              {loadingMore ? 'Loading…' : 'Load more'}
            </button>
          )}
        </div>
      )}
      {subTab === 'MINE' && myAnswers !== null && myAnswers.length > 0 && (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Your answers</h4>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {myAnswers.map((a) => (
              <div key={a.id} className="itunda-flat-section">
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{a.isAdopted ? '✅ Adopted' : 'Pending'}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>{a.body}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function KnowledgeAskCard({ onAsked, categories }: { onAsked: () => void; categories: KnowledgeCategory[] }) {
  const { t } = useI18n();
  const [category, setCategory] = useState('');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={() => setOpen(true)}>
        + Ask a question
      </button>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!category || !title.trim() || !body.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      await postKnowledgeQuestion(category, title, body);
      setCategory(''); setTitle(''); setBody(''); setOpen(false);
      onAsked();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <select
        value={category} onChange={(e) => setCategory(e.target.value)}
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
      >
        <option value="">Choose a category</option>
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      <input
        type="text" value={title} placeholder="Your question" onChange={(e) => setTitle(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <textarea
        value={body} placeholder="Add more detail" onChange={(e) => setBody(e.target.value)} rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'vertical' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting || !category || !title.trim() || !body.trim()}>
        {submitting ? 'Posting…' : 'Post question'}
      </button>
    </form>
  );
}

function KnowledgeQuestionDetailView({ questionId, onBack }: { questionId: string; onBack: () => void }) {
  const { t } = useI18n();
  const [question, setQuestion] = useState<KnowledgeQuestion | null>(null);
  const showQuestionSkeleton = useDeferredLoading(!question);
  const [answers, setAnswers] = useState<KnowledgeAnswer[] | null>(null);
  const showAnswersSkeleton = useDeferredLoading(answers === null);
  const [answerBody, setAnswerBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [answering, setAnswering] = useState(false);
  const [busyAnswerId, setBusyAnswerId] = useState<string | null>(null);
  const currentUser = getStoredUser();

  const load = () => {
    setError(null);
    fetchKnowledgeQuestion(questionId).then(setQuestion).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchKnowledgeAnswers(questionId).then(setAnswers).catch(() => {});
  };

  useEffect(load, [questionId]);

  const handleAnswer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!answerBody.trim()) return;
    setAnswering(true);
    setError(null);
    try {
      await postKnowledgeAnswer(questionId, answerBody);
      setAnswerBody('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setAnswering(false);
    }
  };

  const handleAdopt = async (answerId: string) => {
    setBusyAnswerId(answerId);
    setError(null);
    try {
      await adoptKnowledgeAnswer(questionId, answerId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyAnswerId(null);
    }
  };

  const isAsker = !!question && !!currentUser && question.askerId === currentUser.id;

  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" style={{ marginBottom: '12px' }} onClick={onBack}>← Back</button>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {!question && !error && showQuestionSkeleton && <div className="skeleton" style={{ height: '120px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {question && (
        <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700 }}>{question.title}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-700)', whiteSpace: 'pre-wrap' }}>{question.body}</p>
        </div>
      )}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Answers</h3>
      {answers === null && showAnswersSkeleton && <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {answers !== null && answers.length === 0 && (
        <EmptyState message="No answers yet -- be the first to help." />
      )}
      {answers !== null && answers.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', marginBottom: '12px' }}>
          {answers.map((a) => (
            <div
              key={a.id} className="itunda-flat-section"
              style={a.isAdopted ? { borderLeft: '2.5px solid var(--itunda-indigo)', paddingLeft: '10px' } : undefined}
            >
              {a.isAdopted && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)', marginBottom: '4px' }}>✅ Adopted answer</p>}
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-900)' }}>{a.body}</p>
              {isAsker && !question?.adoptedAnswerId && (
                <button
                  className="itunda-btn itunda-btn-secondary" style={{ marginTop: '8px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                  disabled={busyAnswerId === a.id} onClick={() => handleAdopt(a.id)}
                >
                  {busyAnswerId === a.id ? '…' : 'Adopt this answer'}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
      {!question?.adoptedAnswerId && (
        <form onSubmit={handleAnswer} style={{ display: 'flex', gap: '8px' }}>
          <input
            type="text" value={answerBody} onChange={(e) => setAnswerBody(e.target.value)} placeholder="Write an answer"
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={answering || !answerBody.trim()}>
            {answering ? '…' : 'Send'}
          </button>
        </form>
      )}
    </div>
  );
}

import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import {
  addResumeCertification, addResumeEducation, addResumeExperience,
  fetchMyResume, fetchResumeStrengths, removeResumeCertification, removeResumeEducation, removeResumeExperience,
  updateResumeProfile, type ResumeCertification, type ResumeDetail, type ResumeEducation, type ResumeExperience, type ResumeStrength,
} from './lib/resume';

// Real 이력서 (Karrot 당근알바-style résumé) builder (itunda Hood redesign, 2026-08-28)
// -- see backend Resume.kt's own doc comment. A real completion-percent nudge, same
// reference-sourced "이력서를 완성해보세요 X%" the backend's own completionPercent
// already computes -- this view just renders it, never re-derives it client-side.

const inputStyle: React.CSSProperties = { padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' };

export function ResumeBuilderView() {
  const [detail, setDetail] = useState<ResumeDetail | null>(null);
  const [strengthsCatalog, setStrengthsCatalog] = useState<ResumeStrength[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = () => {
    fetchMyResume().then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your résumé.'));
  };
  useEffect(() => {
    fetchResumeStrengths().then(setStrengthsCatalog).catch(() => {});
    reload();
  }, []);

  if (error && !detail) return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>;
  if (!detail) return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Complete your résumé</h3>
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-brand)' }}>{detail.completionPercent}%</span>
        </div>
        <div style={{ width: '100%', height: '6px', borderRadius: '999px', background: 'var(--itunda-grey-100)' }}>
          <div style={{ width: `${Math.min(100, Math.max(0, detail.completionPercent))}%`, height: '100%', borderRadius: '999px', background: 'var(--itunda-brand)' }} />
        </div>
      </div>
      <ResumeProfileSection detail={detail} strengthsCatalog={strengthsCatalog} onSaved={reload} />
      <ResumeExperienceSection experiences={detail.experiences} onChanged={reload} />
      <ResumeEducationSection educations={detail.educations} onChanged={reload} />
      <ResumeCertificationSection certifications={detail.certifications} onChanged={reload} />
    </div>
  );
}

function ResumeProfileSection({ detail, strengthsCatalog, onSaved }: { detail: ResumeDetail; strengthsCatalog: ResumeStrength[]; onSaved: () => void }) {
  const [selfIntro, setSelfIntro] = useState(detail.resume?.selfIntro ?? '');
  const [additionalInfo, setAdditionalInfo] = useState(detail.resume?.additionalInfo ?? '');
  const [selectedStrengths, setSelectedStrengths] = useState<Set<string>>(
    new Set((detail.resume?.strengths ?? '').split(',').map((s) => s.trim()).filter(Boolean)),
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggleStrength = (id: string) => {
    setSelectedStrengths((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      await updateResumeProfile(selfIntro.trim() || undefined, Array.from(selectedStrengths), additionalInfo.trim() || undefined);
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save your résumé.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>About you</h4>
      <textarea value={selfIntro} onChange={(e) => setSelfIntro(e.target.value)} placeholder="Self-introduction" rows={3} style={{ ...inputStyle, resize: 'vertical' }} />
      {strengthsCatalog.length > 0 && (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          {strengthsCatalog.map((s) => {
            const active = selectedStrengths.has(s.id);
            return (
              <button
                key={s.id} type="button" onClick={() => toggleStrength(s.id)}
                style={{
                  borderRadius: '999px', padding: '7px 12px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 600, border: 'none', cursor: 'pointer',
                  background: active ? 'var(--itunda-brand)' : 'var(--itunda-grey-100)', color: active ? '#fff' : 'var(--itunda-grey-900)',
                }}
              >
                {s.label}
              </button>
            );
          })}
        </div>
      )}
      <textarea value={additionalInfo} onChange={(e) => setAdditionalInfo(e.target.value)} placeholder="Additional info (optional)" rows={2} style={{ ...inputStyle, resize: 'vertical' }} />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <button className="itunda-btn itunda-btn-primary" disabled={saving} onClick={handleSave} style={{ width: 'fit-content' }}>
        {saving ? 'Saving…' : 'Save'}
      </button>
    </div>
  );
}

function ResumeExperienceSection({ experiences, onChanged }: { experiences: ResumeExperience[]; onChanged: () => void }) {
  const [adding, setAdding] = useState(false);
  const [company, setCompany] = useState('');
  const [role, setRole] = useState('');
  const [period, setPeriod] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleAdd = async () => {
    try {
      await addResumeExperience(company.trim(), role.trim(), period.trim());
      setCompany(''); setRole(''); setPeriod(''); setAdding(false); setError(null);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save.');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Experience</h4>
        <button className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }} onClick={() => setAdding(!adding)}>{adding ? 'Cancel' : '+ Add'}</button>
      </div>
      {experiences.map((exp) => (
        <div key={exp.id} style={{ display: 'flex', justifyContent: 'space-between' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{exp.role} · {exp.company}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)' }}>{exp.period}</p>
          </div>
          <button className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }} onClick={() => removeResumeExperience(exp.id).then(onChanged).catch(() => {})}>Remove</button>
        </div>
      ))}
      {adding && (
        <>
          <input value={company} onChange={(e) => setCompany(e.target.value)} placeholder="Company" style={inputStyle} />
          <input value={role} onChange={(e) => setRole(e.target.value)} placeholder="Role" style={inputStyle} />
          <input value={period} onChange={(e) => setPeriod(e.target.value)} placeholder="Period (e.g. 2023 – present)" style={inputStyle} />
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
          <button className="itunda-btn itunda-btn-primary" style={{ width: 'fit-content' }} onClick={handleAdd}>Save</button>
        </>
      )}
    </div>
  );
}

function ResumeEducationSection({ educations, onChanged }: { educations: ResumeEducation[]; onChanged: () => void }) {
  const [adding, setAdding] = useState(false);
  const [school, setSchool] = useState('');
  const [degree, setDegree] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleAdd = async () => {
    try {
      await addResumeEducation(school.trim(), degree.trim() || undefined);
      setSchool(''); setDegree(''); setAdding(false); setError(null);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save.');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Education</h4>
        <button className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }} onClick={() => setAdding(!adding)}>{adding ? 'Cancel' : '+ Add'}</button>
      </div>
      {educations.map((edu) => (
        <div key={edu.id} style={{ display: 'flex', justifyContent: 'space-between' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{[edu.school, edu.degree].filter(Boolean).join(' · ')}</p>
          <button className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }} onClick={() => removeResumeEducation(edu.id).then(onChanged).catch(() => {})}>Remove</button>
        </div>
      ))}
      {adding && (
        <>
          <input value={school} onChange={(e) => setSchool(e.target.value)} placeholder="School" style={inputStyle} />
          <input value={degree} onChange={(e) => setDegree(e.target.value)} placeholder="Degree (optional)" style={inputStyle} />
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
          <button className="itunda-btn itunda-btn-primary" style={{ width: 'fit-content' }} onClick={handleAdd}>Save</button>
        </>
      )}
    </div>
  );
}

function ResumeCertificationSection({ certifications, onChanged }: { certifications: ResumeCertification[]; onChanged: () => void }) {
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleAdd = async () => {
    try {
      await addResumeCertification(name.trim());
      setName(''); setAdding(false); setError(null);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save.');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Certifications</h4>
        <button className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }} onClick={() => setAdding(!adding)}>{adding ? 'Cancel' : '+ Add'}</button>
      </div>
      {certifications.map((cert) => (
        <div key={cert.id} style={{ display: 'flex', justifyContent: 'space-between' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{cert.name}</p>
          <button className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }} onClick={() => removeResumeCertification(cert.id).then(onChanged).catch(() => {})}>Remove</button>
        </div>
      ))}
      {adding && (
        <>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Certification name" style={inputStyle} />
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
          <button className="itunda-btn itunda-btn-primary" style={{ width: 'fit-content' }} onClick={handleAdd}>Save</button>
        </>
      )}
    </div>
  );
}

import { ApiError, BASE_URL, getToken, parseErrorBody } from './api';

// Real photo/document upload (see rw.itunda.marketplace.web.UploadController's own doc
// comment) -- real on backend + Android since 2026-07-24/25, but every bank-mfe flow
// that needs a photo/document (e.g. Property ownership-verification) has so far taken
// a plain documentUrl/imageUrl TEXT field instead, an honest v1 scope-down named in
// this file's own callers. This is bank-mfe's first real client. Uses a raw `fetch`
// (not apiFetch) since apiFetch always sets Content-Type: application/json, which
// would break a multipart/form-data request -- the browser sets the correct boundary
// itself when a body is a FormData and Content-Type is left unset.
export interface UploadResult {
  success: boolean;
  url: string;
}

export async function uploadFile(file: File): Promise<UploadResult> {
  const token = getToken();
  const formData = new FormData();
  formData.append('file', file);
  const response = await fetch(`${BASE_URL}/api/v1/uploads`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
  if (!response.ok) {
    const { code, message } = await parseErrorBody(response);
    throw new ApiError(response.status, code, message);
  }
  return response.json() as Promise<UploadResult>;
}

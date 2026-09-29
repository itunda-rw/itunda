import { ApiError, BASE_URL, getToken, parseErrorBody } from './api';

// Real photo upload (2026-08-13) -- see rw.itunda.marketplace.web.UploadController's
// own doc comment. bank-mfe already has this exact file for Talk photos and profile
// photos; merchant-mfe's product-catalog "Image URL" field (PosScreen.tsx's
// CatalogView) was the one remaining "paste a URL" holdout for a merchant
// photographing their own product on their phone -- not a realistic action for most
// small merchants, the same "businesses are users too" gap the profile-photo fix on
// bank-mfe closed. Uses a raw `fetch` (not apiFetch) since apiFetch always sets
// Content-Type: application/json, which would break a multipart/form-data request.
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

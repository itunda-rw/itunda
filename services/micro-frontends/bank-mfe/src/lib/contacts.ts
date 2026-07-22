import { apiFetch } from './api';

// Real saved-contacts list for quick transfers (rw.itunda.contacts) -- found
// 2026-07-22 fully built on the backend (a real IDOR fix ported from the original
// Express controller: getContacts used to return every user's saved contacts to any
// authenticated caller, filtered by currentUser.userId from the start here, see
// ContactsController.kt's own doc comment) with zero client UI anywhere. bank-mfe's
// own TransferFlow had no recipient picker at all -- just a bare phone/account text
// field.

export interface Contact {
  id: string;
  userId: string;
  name: string;
  bank: string;
  acc: string;
  phoneNumber: string;
  color: string;
  letter: string;
}

export const fetchContacts = () => apiFetch<{ success: boolean; contacts: Contact[] }>('/api/v1/contacts').then((r) => r.contacts);

export const addContact = (name: string, phoneNumber: string, bank?: string) =>
  apiFetch<{ success: boolean; contact: Contact }>('/api/v1/contacts', {
    method: 'POST',
    body: JSON.stringify({ name, phoneNumber, bank }),
  }).then((r) => r.contact);

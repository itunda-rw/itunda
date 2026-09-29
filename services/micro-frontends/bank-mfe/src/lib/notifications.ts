// Real in-app notification inbox (rw.itunda.notifications) -- GET /api/v1/notifications
// + POST /api/v1/notifications/{id}/read, real since day one and already ported to a
// real Notifications section on Android (SettingsScreen.kt) and iOS
// (SettingsViewModel.swift/SettingsScreen.swift), but bank-mfe had zero client for the
// inbox itself despite lib/device.ts already registering push device tokens against
// this same module. Distinct from device-token registration (push delivery) -- this is
// the in-app read/unread list those pushes also get recorded into.
import { apiFetch } from './api';

export interface NotificationItem {
  id: string;
  userId: string;
  type: string;
  title: string;
  body: string;
  isRead: boolean;
  createdAt: string;
  data: Record<string, unknown>;
}

export interface NotificationsResult {
  notifications: NotificationItem[];
  unreadCount: number;
}

export const fetchNotifications = () =>
  apiFetch<{ success: boolean } & NotificationsResult>('/api/v1/notifications').then((r) => r);

export const markNotificationRead = (id: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/notifications/${id}/read`, { method: 'POST' });

export const markAllNotificationsRead = () => markNotificationRead('all');

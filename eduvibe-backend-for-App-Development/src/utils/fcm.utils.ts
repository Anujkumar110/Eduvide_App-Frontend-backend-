import admin from 'firebase-admin';

export const sendFCMNotification = async (
  fcmToken: string,
  title: string,
  body: string,
  data?: Record<string, string>
): Promise<void> => {
  try {
    await admin.messaging().send({
      token: fcmToken,
      notification: { title, body },
      data: data || {},
      android: {
        priority: 'high',
        notification: {
          sound: 'default',
          channelId: 'default',
        },
      },
    });
    console.log(`[FCM] Notification sent successfully`);
  } catch (err: any) {
    console.error(`[FCM] Failed to send notification:`, err.message);
  }
};
import admin from "firebase-admin";
import serviceAccount from "../../serviceAccountKey.json";

export const firebaseApp = admin.initializeApp({
    credential: admin.credential.cert(serviceAccount as admin.ServiceAccount),
});

export const firebaseAuth = firebaseApp.auth();
export const messaging = firebaseApp.messaging();
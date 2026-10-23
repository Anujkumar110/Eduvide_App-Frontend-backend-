import { connectionManager } from "./connectionManager";

export async function publishWsEvent({ channel, event, data }: { channel: string; event: string; data: any }) {
    // Basic implementation that parses channel as `notifications:user-${userId}`
    if (channel.startsWith('notifications:user-')) {
        const userId = channel.replace('notifications:user-', '');
        connectionManager.send(userId, { type: event, payload: data });
    }
}
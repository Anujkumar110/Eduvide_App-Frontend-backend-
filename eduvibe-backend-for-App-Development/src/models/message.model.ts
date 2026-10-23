import mongoose, { Schema, Document } from "mongoose";

export interface IMessage extends Document {
    conversationId: string;
    senderId: string;
    receiverId: string;
    content: string;
    readAt: Date | null;
    createdAt: Date | null
}

const MessageSchema = new Schema<IMessage>(
    {
        conversationId: { type: String, required: true, index: true },
        senderId: { type: String, required: true },
        receiverId: { type: String, required: true },
        content: { type: String, required: true },
        readAt: { type: Date, default: null },
    },
    { timestamps: true }
);

MessageSchema.index({ conversationId: 1, createdAt: -1 });

export const Message = mongoose.model<IMessage>("Message", MessageSchema);
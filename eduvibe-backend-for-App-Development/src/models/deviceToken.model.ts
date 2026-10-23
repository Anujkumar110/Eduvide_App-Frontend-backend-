import mongoose, { Document, Schema, Types } from "mongoose";

export interface IToken extends Document {
    userId: Types.ObjectId;
    token: string;
}

const deviceTokenSchema = new Schema<IToken>({
    userId: { type: Schema.Types.ObjectId, ref: 'User', required: true },
    token: { type: String, required: true }
})

export const DeviceToken = mongoose.model<IToken>('DeviceToken', deviceTokenSchema)
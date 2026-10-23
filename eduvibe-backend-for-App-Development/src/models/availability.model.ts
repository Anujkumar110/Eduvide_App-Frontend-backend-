import mongoose, { Schema, Document, Types } from "mongoose";

export interface IAvailabilitySlot extends Document {
    startTime: string;
    endTime: string;
}

export interface IDayAvailability extends Document {
    isAvailable: boolean;
    slots: IAvailabilitySlot[];
}

export interface ITeacherAvailability {
    monday: IDayAvailability;
    tuesday: IDayAvailability;
    wednesday: IDayAvailability;
    thursday: IDayAvailability;
    friday: IDayAvailability;
    saturday: IDayAvailability;
    sunday: IDayAvailability;
}

const availabilitySlotSchema = new Schema<IAvailabilitySlot>({
    startTime: { type: String, required: true },
    endTime: { type: String, required: true },
}, { _id: false })

export const dayAvailabilitySchema = new Schema<IDayAvailability>({
    isAvailable: { type: Boolean, default: false },
    slots: { type: [availabilitySlotSchema] }
}, { _id: false })
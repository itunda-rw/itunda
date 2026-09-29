-- Real Baemin/Coupang Eats/Uber Eats-style 안심배달 (safe/contactless delivery) proof
-- photo -- see EatsOrder.deliveryProofPhotoUrl's own doc comment.
ALTER TABLE eats_orders ADD COLUMN delivery_proof_photo_url VARCHAR(500) NULL;

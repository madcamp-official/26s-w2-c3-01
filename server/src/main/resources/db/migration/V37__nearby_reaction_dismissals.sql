CREATE TABLE nearby_reaction_dismissals (
    reaction_id UUID NOT NULL REFERENCES nearby_reactions(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dismissed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (reaction_id, recipient_id)
);

CREATE INDEX nearby_reaction_dismissals_recipient_idx
    ON nearby_reaction_dismissals(recipient_id, dismissed_at DESC);

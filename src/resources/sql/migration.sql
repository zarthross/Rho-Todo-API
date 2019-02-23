CREATE TABLE public.todos
(
   id BIGSERIAL PRIMARY KEY,
   msg text NOT NULL,
   is_completed boolean  NOT NULL
) 

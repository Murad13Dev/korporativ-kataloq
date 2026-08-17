import { motion, useReducedMotion, type Variants } from 'framer-motion';
import type { ReactNode } from 'react';

/**
 * Scroll-triggered entrance for section content.
 *
 * `whileInView` with `once: true` fires the moment a section's header or grid crosses
 * into the viewport and never re-fires on scroll-back — a page that re-animates every
 * time you scroll past it reads as gimmicky rather than polished. Reduced-motion users
 * get the end state immediately with no transform, matching the CSS-level override in
 * theme.css for everything this component's sibling components animate with plain CSS.
 */

const DIRECTIONS = {
  up: { y: 18 },
  down: { y: -18 },
  left: { x: 18 },
  right: { x: -18 },
  none: {},
} as const;

// A fixed, explicit map rather than dynamic `motion[as]` indexing — keeps every variant
// statically known to TypeScript and avoids handing an arbitrary string to framer-motion.
const MOTION_TAGS = {
  div: motion.div,
  section: motion.section,
  li: motion.li,
  article: motion.article,
} as const;

type RevealTag = keyof typeof MOTION_TAGS;

export function Reveal({
  children,
  as = 'div',
  direction = 'up',
  delay = 0,
  duration = 0.5,
  className,
  once = true,
  amount = 0.2,
}: {
  children: ReactNode;
  as?: RevealTag;
  direction?: keyof typeof DIRECTIONS;
  delay?: number;
  duration?: number;
  className?: string;
  once?: boolean;
  amount?: number;
}) {
  const reduceMotion = useReducedMotion();
  const offset = DIRECTIONS[direction];

  if (reduceMotion) {
    const Plain = as;
    return <Plain className={className}>{children}</Plain>;
  }

  const MotionTag = MOTION_TAGS[as];

  return (
    <MotionTag
      className={className}
      initial={{ opacity: 0, ...offset }}
      whileInView={{ opacity: 1, x: 0, y: 0 }}
      viewport={{ once, amount }}
      transition={{ duration, delay, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </MotionTag>
  );
}

/**
 * Applies a staggered `Reveal` to each child, so a grid of cards enters as a wave
 * rather than all at once. Caps the visible stagger at 10 items — beyond that the
 * delay would make the tail of a long grid noticeably late to appear.
 */
export function RevealGroup({
  children,
  className,
  itemClassName,
  staggerDelay = 0.06,
  direction = 'up',
}: {
  children: ReactNode[];
  className?: string;
  itemClassName?: string;
  staggerDelay?: number;
  direction?: keyof typeof DIRECTIONS;
}) {
  return (
    <div className={className}>
      {children.map((child, index) => (
        <Reveal
          // eslint-disable-next-line react/no-array-index-key
          key={index}
          direction={direction}
          delay={Math.min(index, 10) * staggerDelay}
          className={itemClassName}
        >
          {child}
        </Reveal>
      ))}
    </div>
  );
}

export const fadeUpVariants: Variants = {
  hidden: { opacity: 0, y: 18 },
  visible: (delay: number = 0) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.5, delay, ease: [0.22, 1, 0.36, 1] },
  }),
};
